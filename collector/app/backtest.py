"""Portfolio backtesting -- Phase 1: equal-weight buy & hold vs a benchmark (SPY), computed
entirely from price_history's already-collected daily bars. No fresh yfinance calls here on
purpose: backtests should be fast and independent of Yahoo-side flakiness (see today's
CollectorClient timeout incident), and price_history already has years of daily bars for the
S&P 500 universe.

`_load_closes` (DB I/O) and `_compute_backtest` (pure) are kept separate so the math is directly
unit-testable, same reasoning as aggregator.py.
"""

from datetime import date

import pandas as pd

from . import mysql_writer

BENCHMARK_TICKER = "SPY"
TRADING_DAYS_PER_YEAR = 252


class InsufficientDataError(Exception):
    pass


def _load_closes(tickers: list[str], start: date, end: date) -> pd.DataFrame:
    """Daily closes for `tickers` as a wide DataFrame (index=date, columns=ticker), inner-joined
    so every row has data for every requested ticker -- missing-data rows are dropped rather than
    forward-filled, since a backtest silently carrying stale prices would be misleading.

    Resolves ticker -> symbol_id first and queries price_history by symbol_id (not a ticker JOIN)
    -- see today's PROGRESS.md note: a symbols JOIN + ORDER BY defeats the
    (symbol_id, timeframe, ts) index and forces a full table scan once price_history is large.
    """
    conn = mysql_writer.connect()
    try:
        with conn.cursor() as cur:
            placeholders = ",".join(["%s"] * len(tickers))
            cur.execute(f"SELECT id, ticker FROM symbols WHERE ticker IN ({placeholders})", tickers)
            id_to_ticker = {symbol_id: ticker for symbol_id, ticker in cur.fetchall()}
            if not id_to_ticker:
                raise InsufficientDataError("No matching symbols found")

            id_placeholders = ",".join(["%s"] * len(id_to_ticker))
            cur.execute(
                f"""
                SELECT symbol_id, ts, close FROM price_history
                WHERE symbol_id IN ({id_placeholders}) AND timeframe = '1d' AND ts BETWEEN %s AND %s
                ORDER BY ts
                """,
                (*id_to_ticker.keys(), start, end),
            )
            rows = cur.fetchall()
    finally:
        conn.close()

    if not rows:
        raise InsufficientDataError("No price data for the given tickers/date range")

    df = pd.DataFrame(rows, columns=["symbol_id", "ts", "close"])
    df["ticker"] = df["symbol_id"].map(id_to_ticker)
    df["date"] = pd.to_datetime(df["ts"]).dt.date
    wide = df.pivot_table(index="date", columns="ticker", values="close").dropna()
    if wide.empty:
        raise InsufficientDataError("No overlapping trading days across the selected tickers/benchmark")
    return wide


def _compute_backtest(
    closes: pd.DataFrame,
    tickers: list[str],
    initial_capital: float,
    risk_free_rate: float,
) -> dict:
    """Pure: given a wide closes DataFrame (must already include `tickers` and BENCHMARK_TICKER
    columns), computes an equal-weight buy & hold equity curve vs the benchmark, plus summary
    risk/return metrics. Fractional shares assumed -- this is a valuation model, not an order book.
    """
    portfolio_tickers = [t for t in tickers if t in closes.columns]
    if not portfolio_tickers:
        raise InsufficientDataError("None of the requested tickers have price data in range")
    if BENCHMARK_TICKER not in closes.columns:
        raise InsufficientDataError(f"No price data for benchmark {BENCHMARK_TICKER}")

    weight = 1.0 / len(portfolio_tickers)
    normalized = closes[portfolio_tickers] / closes[portfolio_tickers].iloc[0]
    portfolio_value = (normalized * weight).sum(axis=1) * initial_capital
    benchmark_value = closes[BENCHMARK_TICKER] / closes[BENCHMARK_TICKER].iloc[0] * initial_capital

    equity_curve = [
        {"date": d.isoformat(), "portfolioValue": round(float(pv), 2), "benchmarkValue": round(float(bv), 2)}
        for d, pv, bv in zip(closes.index, portfolio_value, benchmark_value)
    ]

    daily_returns = portfolio_value.pct_change().dropna()
    total_return_pct = (portfolio_value.iloc[-1] / portfolio_value.iloc[0] - 1) * 100

    years = (closes.index[-1] - closes.index[0]).days / 365.25
    cagr_pct = ((portfolio_value.iloc[-1] / portfolio_value.iloc[0]) ** (1 / years) - 1) * 100 if years > 0 else None

    running_max = portfolio_value.cummax()
    drawdown_pct = (portfolio_value - running_max) / running_max * 100
    max_drawdown_pct = drawdown_pct.min()

    volatility_pct = daily_returns.std() * (TRADING_DAYS_PER_YEAR**0.5) * 100 if len(daily_returns) > 1 else None
    annualized_return = daily_returns.mean() * TRADING_DAYS_PER_YEAR if len(daily_returns) > 1 else None
    sharpe_ratio = (
        (annualized_return - risk_free_rate) / (daily_returns.std() * (TRADING_DAYS_PER_YEAR**0.5))
        if annualized_return is not None and daily_returns.std() > 0
        else None
    )

    return {
        "equityCurve": equity_curve,
        "metrics": {
            "totalReturnPct": round(float(total_return_pct), 2),
            "cagrPct": round(float(cagr_pct), 2) if cagr_pct is not None else None,
            "maxDrawdownPct": round(float(max_drawdown_pct), 2),
            "volatilityPct": round(float(volatility_pct), 2) if volatility_pct is not None else None,
            "sharpeRatio": round(float(sharpe_ratio), 2) if sharpe_ratio is not None else None,
        },
    }


def run_backtest(
    tickers: list[str],
    start: date,
    end: date,
    initial_capital: float,
    risk_free_rate: float,
) -> dict:
    all_tickers = list(dict.fromkeys([*tickers, BENCHMARK_TICKER]))
    closes = _load_closes(all_tickers, start, end)
    return _compute_backtest(closes, tickers, initial_capital, risk_free_rate)
