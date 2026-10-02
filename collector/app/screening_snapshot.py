"""Build and atomically publish the stock screener snapshot."""

import logging
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone

import pandas as pd

from . import mysql_writer
from .screener import ENRICHMENT_WORKERS, InsufficientScreenerDataError, _enrich
from .technical_indicators import atr, bollinger, ema, macd, relative_volume, wilder_rsi

logger = logging.getLogger("collector.screening_snapshot")

_run_lock = threading.Lock()
_INSERT_SQL = """
INSERT INTO stock_screening_snapshots (
    snapshot_run_id, symbol_id, snapshot_date, price,
    momentum_3m, momentum_6m, momentum_12m, volatility_20d, rsi_14,
    sma_50, sma_100, sma_200, above_sma_200,
    ema_20, ema_60, macd_line, macd_signal, macd_histogram,
    bollinger_percent_b_20, atr_pct_14, relative_volume_20,
    market_cap, revenue_ttm, revenue_growth, roe, profit_margin, trailing_pe,
    news_sentiment, news_count, calculated_at
) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
ON DUPLICATE KEY UPDATE
    price=VALUES(price), momentum_3m=VALUES(momentum_3m), momentum_6m=VALUES(momentum_6m),
    momentum_12m=VALUES(momentum_12m), volatility_20d=VALUES(volatility_20d),
    rsi_14=VALUES(rsi_14), sma_50=VALUES(sma_50), sma_100=VALUES(sma_100),
    sma_200=VALUES(sma_200), above_sma_200=VALUES(above_sma_200),
    ema_20=VALUES(ema_20), ema_60=VALUES(ema_60), macd_line=VALUES(macd_line),
    macd_signal=VALUES(macd_signal), macd_histogram=VALUES(macd_histogram),
    bollinger_percent_b_20=VALUES(bollinger_percent_b_20), atr_pct_14=VALUES(atr_pct_14),
    relative_volume_20=VALUES(relative_volume_20),
    market_cap=VALUES(market_cap), revenue_ttm=VALUES(revenue_ttm),
    revenue_growth=VALUES(revenue_growth), roe=VALUES(roe), profit_margin=VALUES(profit_margin),
    trailing_pe=VALUES(trailing_pe), news_sentiment=VALUES(news_sentiment),
    news_count=VALUES(news_count), calculated_at=VALUES(calculated_at)
"""


class SnapshotAlreadyRunningError(Exception):
    pass


def _pct(prices, days):
    return round(float((prices.iloc[-1] / prices.iloc[-1 - days] - 1) * 100), 4)


def _rounded(value):
    return None if value is None else round(value, 4)


def _load_universe_history() -> dict[str, pd.DataFrame]:
    conn = mysql_writer.connect()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id,ticker FROM symbols WHERE in_sp500_universe=TRUE")
            id_to_ticker = {symbol_id: ticker for symbol_id, ticker in cur.fetchall()}
            if not id_to_ticker:
                raise InsufficientScreenerDataError("No S&P 500 symbols found")
            cur.execute("SELECT MAX(ts) FROM price_history WHERE timeframe='1d'")
            (latest_ts,) = cur.fetchone()
            if latest_ts is None:
                raise InsufficientScreenerDataError("No price history available")
            placeholders = ",".join(["%s"] * len(id_to_ticker))
            cur.execute(
                f"""
                SELECT symbol_id,ts,high,low,close,volume FROM price_history
                WHERE symbol_id IN ({placeholders}) AND timeframe='1d' AND ts >= %s
                ORDER BY ts
                """,
                (*id_to_ticker.keys(), latest_ts - timedelta(days=500)),
            )
            rows = cur.fetchall()
    finally:
        conn.close()
    if not rows:
        raise InsufficientScreenerDataError("No S&P 500 price history available")
    frame = pd.DataFrame(rows, columns=["symbol_id", "ts", "high", "low", "close", "volume"])
    frame["ticker"] = frame["symbol_id"].map(id_to_ticker)
    frame["date"] = pd.to_datetime(frame["ts"]).dt.date
    for column in ["high", "low", "close", "volume"]:
        frame[column] = frame[column].astype(float)
    return {
        ticker: group.set_index("date")[["high", "low", "close", "volume"]]
        for ticker, group in frame.groupby("ticker", sort=False)
    }


def _technical_metrics(history):
    if isinstance(history, pd.Series):
        prices = history.dropna()
        frame = None
    else:
        frame = history.dropna(subset=["high", "low", "close", "volume"])
        prices = frame["close"]
    if len(prices) < 253:
        return None
    returns = prices.pct_change().dropna()
    if returns.abs().max() > 0.5:
        return None
    sma50 = float(prices.tail(50).mean())
    sma100 = float(prices.tail(100).mean())
    sma200 = float(prices.tail(200).mean())
    macd_values = macd(prices)
    bands = bollinger(prices)
    atr_value = atr(frame, 14) if frame is not None else None
    return {
        "price": float(prices.iloc[-1]),
        "momentum3m": _pct(prices, 63),
        "momentum6m": _pct(prices, 126),
        "momentum12m": _pct(prices, 252),
        "volatility20d": round(float(returns.tail(20).std() * (252**0.5) * 100), 4),
        "rsi14": _rounded(wilder_rsi(prices)),
        "sma50": sma50,
        "sma100": sma100,
        "sma200": sma200,
        "aboveSma200": bool(prices.iloc[-1] > sma200),
        "ema20": _rounded(ema(prices, 20)),
        "ema60": _rounded(ema(prices, 60)),
        "macdLine": _rounded(macd_values["line"]),
        "macdSignal": _rounded(macd_values["signal"]),
        "macdHistogram": _rounded(macd_values["histogram"]),
        "bollingerPercentB20": _rounded(bands["percentB"]),
        "atrPct14": _rounded(None if atr_value is None else atr_value / float(prices.iloc[-1]) * 100),
        "relativeVolume20": _rounded(None if frame is None else relative_volume(frame["volume"], 20)),
    }


def _start_run(started_at):
    conn = mysql_writer.connect()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "INSERT INTO screening_snapshot_runs (snapshot_date,status,started_at) VALUES (%s,'RUNNING',%s)",
                (started_at.date(), started_at),
            )
            return cur.lastrowid
    finally:
        conn.close()


def _mark_failed(run_id, exc):
    conn = mysql_writer.connect()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE screening_snapshot_runs SET status='FAILED',completed_at=%s,error_message=%s WHERE id=%s",
                (datetime.now(timezone.utc), str(exc)[:1000], run_id),
            )
    finally:
        conn.close()


def _persist(run_id, snapshot_date, calculated_at, rows):
    conn = mysql_writer.connect()
    try:
        conn.autocommit(False)
        with conn.cursor() as cur:
            cur.execute("SELECT id,ticker FROM symbols WHERE in_sp500_universe=TRUE")
            symbol_ids = {ticker: symbol_id for symbol_id, ticker in cur.fetchall()}
            values = [
                (
                    run_id, symbol_ids[row["ticker"]], snapshot_date, row["price"], row["momentum3m"],
                    row["momentum6m"], row["momentum12m"], row["volatility20d"], row["rsi14"],
                    row["sma50"], row["sma100"], row["sma200"], row["aboveSma200"],
                    row["ema20"], row["ema60"], row["macdLine"], row["macdSignal"], row["macdHistogram"],
                    row["bollingerPercentB20"], row["atrPct14"], row["relativeVolume20"],
                    row["marketCap"], row["totalRevenue"], row["revenueGrowthPct"],
                    row["returnOnEquityPct"], row["profitMarginPct"], row["trailingPE"],
                    row["newsSentiment"], row["newsCount"], calculated_at,
                )
                for row in rows
                if row["ticker"] in symbol_ids
            ]
            if not values:
                raise ValueError("No snapshot rows matched S&P 500 symbols")
            cur.executemany(_INSERT_SQL, values)
            cur.execute(
                "UPDATE screening_snapshot_runs SET status='COMPLETED',snapshot_date=%s,completed_at=%s,symbol_count=%s WHERE id=%s",
                (snapshot_date, datetime.now(timezone.utc), len(values), run_id),
            )
        conn.commit()
        return run_id, len(values)
    except Exception as exc:
        conn.rollback()
        raise
    finally:
        conn.close()


def run_snapshot_batch() -> dict:
    if not _run_lock.acquire(blocking=False):
        raise SnapshotAlreadyRunningError("Screening snapshot batch is already running")
    started_at = datetime.now(timezone.utc)
    started_ns = time.perf_counter_ns()
    timings = {}
    run_id = None
    try:
        run_id = _start_run(started_at)
        stage = time.perf_counter_ns()
        histories = _load_universe_history()
        timings["snapshot.batch.db_load"] = (time.perf_counter_ns() - stage) / 1_000_000

        stage = time.perf_counter_ns()
        technical = {ticker: metrics for ticker, history in histories.items() if (metrics := _technical_metrics(history))}
        if not technical:
            raise ValueError("No symbols have sufficient price history for a snapshot")
        timings["snapshot.batch.cpu"] = (time.perf_counter_ns() - stage) / 1_000_000

        stage = time.perf_counter_ns()
        enrichment = {}
        with ThreadPoolExecutor(max_workers=ENRICHMENT_WORKERS) as executor:
            futures = {executor.submit(_enrich, ticker): ticker for ticker in technical}
            for future in as_completed(futures):
                enrichment[futures[future]] = future.result()
        timings["snapshot.batch.enrichment"] = (time.perf_counter_ns() - stage) / 1_000_000

        rows = [{"ticker": ticker, **metrics, **enrichment[ticker]} for ticker, metrics in technical.items()]
        stage = time.perf_counter_ns()
        snapshot_date = max(max(history.index) for history in histories.values())
        _, symbol_count = _persist(run_id, snapshot_date, started_at, rows)
        timings["snapshot.batch.persist"] = (time.perf_counter_ns() - stage) / 1_000_000
        timings["snapshot.batch.total"] = (time.perf_counter_ns() - started_ns) / 1_000_000
        return {
            "runId": run_id,
            "status": "COMPLETED",
            "startedAt": started_at,
            "completedAt": datetime.now(timezone.utc),
            "symbolCount": symbol_count,
            "timingsMs": timings,
        }
    except Exception as exc:
        if run_id is not None:
            _mark_failed(run_id, exc)
        raise
    finally:
        _run_lock.release()
