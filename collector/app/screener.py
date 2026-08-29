"""Momentum stock screener over the S&P 500 universe.

Ranking (momentum/volatility/trend/RSI) is computed purely from price_history, already collected
for the whole S&P 500 universe by sp500_universe.py's batch job -- same "no fresh yfinance calls"
reasoning as backtest.py, and the same reason this is DB-only rather than a per-ticker yf.download
loop over ~500 symbols. Fundamentals + news sentiment are only fetched live for the final
shortlist (~10 tickers after momentum ranking + correlation-based diversification), which keeps
the external-call count small enough to do synchronously within one request.
"""

import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import timedelta

import pandas as pd
from textblob import TextBlob

logger = logging.getLogger("collector.screener")

# Defaults, all user-adjustable via run_screener except VOLATILITY_WINDOW_DAYS/RSI_WINDOW/
# CORRELATION_LOOKBACK_DAYS -- those three are display/diversification internals a screening
# condition wouldn't normally target, left fixed at the conventional values the reference
# momentum-portfolio scripts used, same reasoning backtest.py applies to its own fixed windows.
DEFAULT_MOMENTUM_WINDOW_DAYS = 126  # ~6 trading months
DEFAULT_TREND_MA_WINDOW = 200
DEFAULT_CORRELATION_THRESHOLD = 0.6
VOLATILITY_WINDOW_DAYS = 20
RSI_WINDOW = 14
CORRELATION_LOOKBACK_DAYS = 126
TRADING_DAYS_PER_YEAR = 252
MAX_TOP_N = 20
MAX_MOMENTUM_WINDOW_DAYS = 252  # ~1 trading year -- keeps the DB load window bounded
MAX_TREND_MA_WINDOW = 252
ENRICHMENT_WORKERS = 5
# Market cap/revenue can only be checked on the shortlist (they need a live yfinance call, unlike
# the DB-only momentum/RSI/trend metrics) -- so a strict cap/revenue filter can knock candidates
# out *after* the top-N momentum picks are already chosen. Enriching a larger pool up front (still
# in momentum-ranked order) and then taking the first N that pass gives filtered results an actual
# chance to backfill instead of just shrinking the result count. Capped to bound worst-case latency
# (each extra candidate is one more live yfinance + Finnhub call).
ENRICHMENT_POOL_MULTIPLIER = 2
MAX_ENRICHMENT_POOL = 20


class InsufficientScreenerDataError(Exception):
    pass


class InvalidScreenerParamsError(Exception):
    pass


def _load_universe_closes(momentum_window_days: int, trend_ma_window: int) -> pd.DataFrame:
    """Wide DataFrame (index=date, columns=ticker) of S&P 500 closes, wide enough for the 200-day
    trend filter plus 6-month momentum. Unlike backtest.py's _load_closes, this does NOT dropna()
    across the whole frame -- with ~500 tickers, any single gap (new IPO, halt, data hiccup) would
    wipe out nearly every row. Each ticker's series is cleaned independently in _ticker_metrics.

    Resolves symbol_id -> ticker first and queries price_history by symbol_id (not a symbols JOIN)
    -- same reasoning as _load_closes in backtest.py: a JOIN + WHERE-on-the-joined-table defeats
    the (symbol_id, timeframe, ts) index and forces a full table scan (observed firsthand: ~26s for
    this query joined vs ~1s resolving ids first, on this same price_history table).
    """
    from . import mysql_writer

    conn = mysql_writer.connect()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, ticker FROM symbols WHERE in_sp500_universe = 1")
            id_to_ticker = {symbol_id: ticker for symbol_id, ticker in cur.fetchall()}
            if not id_to_ticker:
                raise InsufficientScreenerDataError("No S&P 500 symbols found")

            cur.execute("SELECT MAX(ts) FROM price_history WHERE timeframe = '1d'")
            (latest_ts,) = cur.fetchone()
            if latest_ts is None:
                raise InsufficientScreenerDataError("No price history available")
            # *1.6 pads trading days -> calendar days for weekends/holidays.
            start = latest_ts - timedelta(days=int((trend_ma_window + momentum_window_days) * 1.6))

            id_placeholders = ",".join(["%s"] * len(id_to_ticker))
            cur.execute(
                f"""
                SELECT symbol_id, ts, close FROM price_history
                WHERE symbol_id IN ({id_placeholders}) AND timeframe = '1d' AND ts >= %s
                ORDER BY ts
                """,
                (*id_to_ticker.keys(), start),
            )
            rows = cur.fetchall()
    finally:
        conn.close()

    if not rows:
        raise InsufficientScreenerDataError("No S&P 500 price history available")

    # `close` comes back as decimal.Decimal (price_history is a DECIMAL column) -- casting to
    # float up front lets pivot() below build a native float64 frame directly. Left as Decimal,
    # pivot_table's default mean-aggregation falls into a slow non-vectorized per-cell Python path
    # across 500+ columns (observed firsthand: ~12s vs <1s here for the same data, once cast).
    # pivot() (not pivot_table()) is also correct, not just faster: price_history has one row per
    # (symbol_id, timeframe, ts), so there's nothing to aggregate -- pivot() enforces that
    # uniqueness instead of silently averaging if it were ever violated.
    df = pd.DataFrame([(sid, ts, float(close)) for sid, ts, close in rows], columns=["symbol_id", "ts", "close"])
    df["ticker"] = df["symbol_id"].map(id_to_ticker)
    df["date"] = pd.to_datetime(df["ts"]).dt.date
    return df.pivot(index="date", columns="ticker", values="close")


def _rsi(prices: pd.Series, window: int = RSI_WINDOW) -> float | None:
    delta = prices.diff().dropna()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)
    avg_gain = gain.rolling(window).mean().iloc[-1]
    avg_loss = loss.rolling(window).mean().iloc[-1]
    if pd.isna(avg_gain) or pd.isna(avg_loss):
        return None
    if avg_loss == 0:
        return 100.0
    rs = avg_gain / avg_loss
    return float(100 - (100 / (1 + rs)))


def _ticker_metrics(prices: pd.Series, momentum_window_days: int, trend_ma_window: int) -> dict | None:
    """None means "not enough history to screen this ticker" (recent IPO, long data gap, etc.) --
    it's just excluded from the ranking rather than raising, since one thin ticker shouldn't fail
    the whole screener.
    """
    prices = prices.dropna()
    if len(prices) < trend_ma_window + momentum_window_days:
        return None

    daily_returns = prices.pct_change().dropna()
    # A single-day move past this is essentially never real for an S&P 500 constituent -- far more
    # likely an unadjusted stock split, bad print, or other price_history data glitch (observed
    # firsthand: MRNA showing a +177% one-day "gain" that was clearly bad data). Excluding rather
    # than screening on it protects the ranking from being dominated by data artifacts.
    if daily_returns.abs().max() > 0.5:
        return None

    momentum_pct = float((prices.iloc[-1] / prices.iloc[-1 - momentum_window_days] - 1) * 100)

    recent_returns = daily_returns.tail(VOLATILITY_WINDOW_DAYS)
    if len(recent_returns) < 2:
        return None
    volatility_pct = float(recent_returns.std() * (TRADING_DAYS_PER_YEAR**0.5) * 100)

    trend_ma = prices.rolling(trend_ma_window).mean().iloc[-1]
    if pd.isna(trend_ma):
        return None
    trend_up = bool(prices.iloc[-1] > trend_ma)

    rsi14 = _rsi(prices)
    if rsi14 is None:
        return None

    return {
        "momentumPct": round(momentum_pct, 2),
        "volatilityPct": round(volatility_pct, 2),
        "trendUp": trend_up,
        "rsi14": round(rsi14, 2),
    }


def _select_diversified(ranked_tickers: list[str], closes: pd.DataFrame, top_n: int, correlation_threshold: float) -> list[str]:
    """Greedily walk the momentum-ranked list, skipping any candidate whose trailing daily-return
    correlation with an already-picked name exceeds correlation_threshold -- same diversification
    rule as the reference momentum-portfolio scripts, so the shortlist isn't just one crowded trade.
    """
    if not ranked_tickers:
        return []
    returns = closes[ranked_tickers].tail(CORRELATION_LOOKBACK_DAYS).pct_change()
    corr = returns.corr()

    selected: list[str] = []
    for ticker in ranked_tickers:
        if len(selected) >= top_n:
            break
        is_correlated = any(corr.loc[ticker, s] > correlation_threshold for s in selected)
        if not is_correlated:
            selected.append(ticker)
    return selected


def _pct_or_none(fraction) -> float | None:
    return round(float(fraction) * 100, 2) if fraction is not None else None


_EMPTY_FUNDAMENTALS = {
    "revenueGrowthPct": None,
    "returnOnEquityPct": None,
    "profitMarginPct": None,
    "trailingPE": None,
    "marketCap": None,
    "totalRevenue": None,
}


def _fetch_fundamentals(ticker: str) -> dict:
    try:
        import yfinance as yf

        info = yf.Ticker(ticker).info
        return {
            "revenueGrowthPct": _pct_or_none(info.get("revenueGrowth")),
            "returnOnEquityPct": _pct_or_none(info.get("returnOnEquity")),
            "profitMarginPct": _pct_or_none(info.get("profitMargins")),
            "trailingPE": info.get("trailingPE"),
            "marketCap": info.get("marketCap"),
            "totalRevenue": info.get("totalRevenue"),
        }
    except Exception:
        logger.warning("Fundamentals fetch failed for %s", ticker, exc_info=True)
        return dict(_EMPTY_FUNDAMENTALS)


def _fetch_sentiment(ticker: str) -> dict:
    try:
        from .news import get_company_news

        articles = get_company_news(ticker, days=14)
        headlines = [a.get("headline", "") for a in articles if a.get("headline")]
        if not headlines:
            return {"newsSentiment": None, "newsCount": 0}
        scores = [TextBlob(h).sentiment.polarity for h in headlines]
        return {"newsSentiment": round(sum(scores) / len(scores), 3), "newsCount": len(headlines)}
    except Exception:
        logger.warning("News sentiment fetch failed for %s", ticker, exc_info=True)
        return {"newsSentiment": None, "newsCount": 0}


def _enrich(ticker: str) -> dict:
    return {**_fetch_fundamentals(ticker), **_fetch_sentiment(ticker)}


def run_screener(
    top_n: int = 10,
    momentum_window_days: int = DEFAULT_MOMENTUM_WINDOW_DAYS,
    trend_ma_window: int = DEFAULT_TREND_MA_WINDOW,
    correlation_threshold: float = DEFAULT_CORRELATION_THRESHOLD,
    min_momentum_pct: float | None = None,
    max_rsi: float | None = None,
    min_market_cap: float | None = None,
    min_revenue: float | None = None,
) -> dict:
    top_n = max(1, min(top_n, MAX_TOP_N))
    if not (0 < momentum_window_days <= MAX_MOMENTUM_WINDOW_DAYS):
        raise InvalidScreenerParamsError(f"momentumWindowDays must be between 1 and {MAX_MOMENTUM_WINDOW_DAYS}")
    if not (0 < trend_ma_window <= MAX_TREND_MA_WINDOW):
        raise InvalidScreenerParamsError(f"trendMaWindow must be between 1 and {MAX_TREND_MA_WINDOW}")
    if not (0 < correlation_threshold <= 1):
        raise InvalidScreenerParamsError("correlationThreshold must be between 0 and 1")
    if max_rsi is not None and not (0 <= max_rsi <= 100):
        raise InvalidScreenerParamsError("maxRsi must be between 0 and 100")
    if min_market_cap is not None and min_market_cap < 0:
        raise InvalidScreenerParamsError("minMarketCap must be non-negative")
    if min_revenue is not None and min_revenue < 0:
        raise InvalidScreenerParamsError("minRevenue must be non-negative")

    closes = _load_universe_closes(momentum_window_days, trend_ma_window)
    metrics_by_ticker: dict[str, dict] = {}
    for ticker in closes.columns:
        metrics = _ticker_metrics(closes[ticker], momentum_window_days, trend_ma_window)
        if metrics is not None:
            metrics_by_ticker[ticker] = metrics

    candidates = sorted(
        (
            t
            for t, m in metrics_by_ticker.items()
            if m["trendUp"]
            and (min_momentum_pct is None or m["momentumPct"] >= min_momentum_pct)
            and (max_rsi is None or m["rsi14"] <= max_rsi)
        ),
        key=lambda t: metrics_by_ticker[t]["momentumPct"],
        reverse=True,
    )

    # Market cap/revenue can only be checked after a live fetch, so enrich a larger pool (still in
    # momentum-ranked + diversified order) up front -- otherwise a strict cap/revenue filter would
    # just shrink the final result count instead of backfilling from the next-best candidates.
    pool_size = min(len(candidates), top_n * ENRICHMENT_POOL_MULTIPLIER, MAX_ENRICHMENT_POOL)
    pool = _select_diversified(candidates, closes, pool_size, correlation_threshold)

    enrichment: dict[str, dict] = {}
    with ThreadPoolExecutor(max_workers=ENRICHMENT_WORKERS) as pool_executor:
        future_to_ticker = {pool_executor.submit(_enrich, t): t for t in pool}
        for future in as_completed(future_to_ticker):
            enrichment[future_to_ticker[future]] = future.result()

    selected: list[str] = []
    for ticker in pool:
        e = enrichment[ticker]
        if min_market_cap is not None and (e["marketCap"] is None or e["marketCap"] < min_market_cap):
            continue
        if min_revenue is not None and (e["totalRevenue"] is None or e["totalRevenue"] < min_revenue):
            continue
        selected.append(ticker)
        if len(selected) >= top_n:
            break

    results = [{"ticker": t, **metrics_by_ticker[t], **enrichment[t]} for t in selected]

    return {
        "universeSize": len(closes.columns),
        "screenedCount": len(metrics_by_ticker),
        "candidateCount": len(candidates),
        "results": results,
    }
