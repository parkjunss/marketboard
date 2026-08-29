"""Portfolio backtesting -- Phase 1: equal-weight buy & hold vs a benchmark (SPY), computed
entirely from price_history's already-collected daily bars. No fresh yfinance calls here on
purpose: backtests should be fast and independent of Yahoo-side flakiness (see today's
CollectorClient timeout incident), and price_history already has years of daily bars for the
S&P 500 universe.

`_load_closes` (DB I/O) and `_compute_backtest` (pure) are kept separate so the math is directly
unit-testable, same reasoning as aggregator.py.
"""

from datetime import date, timedelta

import pandas as pd

BENCHMARK_TICKER = "SPY"
VIX_TICKER = "^VIX"
TRADING_DAYS_PER_YEAR = 252

STRATEGY_BUY_AND_HOLD = "BUY_AND_HOLD"
STRATEGY_SMA_CROSSOVER = "SMA_CROSSOVER"
STRATEGY_PERIODIC_REBALANCE = "PERIODIC_REBALANCE"
STRATEGY_VOLATILITY_TARGET = "VOLATILITY_TARGET"

_REBALANCE_FREQUENCY_TO_PANDAS_PERIOD = {"MONTHLY": "M", "QUARTERLY": "Q", "YEARLY": "Y"}

# Fixed conventions (not user-tunable), matching the reference vol-targeting strategies this was
# ported from: a 200-day SPY regime filter and a 20-day realized-vol lookback.
VOL_TARGET_TREND_MA_WINDOW = 200
VOL_TARGET_VOL_LOOKBACK_WINDOW = 20


class InsufficientDataError(Exception):
    pass


class InvalidStrategyParamsError(Exception):
    pass


def _load_closes(tickers: list[str], start: date, end: date) -> pd.DataFrame:
    """Daily closes for `tickers` as a wide DataFrame (index=date, columns=ticker), inner-joined
    so every row has data for every requested ticker -- missing-data rows are dropped rather than
    forward-filled, since a backtest silently carrying stale prices would be misleading.

    Resolves ticker -> symbol_id first and queries price_history by symbol_id (not a ticker JOIN)
    -- see today's PROGRESS.md note: a symbols JOIN + ORDER BY defeats the
    (symbol_id, timeframe, ts) index and forces a full table scan once price_history is large.
    """
    # Imported lazily so importing _compute_backtest (the pure part, unit-tested without a DB)
    # doesn't transitively pull in config.py, which hard-requires FINNHUB_API_KEY at import time
    # -- CI's collector test job intentionally runs with no env vars/secrets set.
    from . import mysql_writer

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


def _load_vix_closes(start: date, end: date) -> pd.Series:
    """Daily VIX closes, fetched fresh from yfinance -- unlike _load_closes, VIX isn't part of the
    regularly-tracked/backfilled symbol universe (it's not a tradable position, just a regime
    signal), so there's no price_history table to read it from. This is the one deliberate
    exception to this module's "no fresh yfinance calls" rule, same precedent as
    market_indices.py's live index fetches. Only called when VOLATILITY_TARGET is selected.
    """
    import yfinance as yf

    data = yf.Ticker(VIX_TICKER).history(start=start, end=end + timedelta(days=1), interval="1d")
    if data.empty:
        raise InsufficientDataError("No VIX data available for the given date range")
    closes = data["Close"]
    closes.index = [ts.date() for ts in closes.index]
    return closes


def _return_and_volatility(prices: pd.Series) -> tuple[float, float | None]:
    """Total return and annualized volatility for a raw price series (not an equity curve) --
    used per-ticker/benchmark for the return-vs-volatility scatter, independent of portfolio weighting.
    """
    total_return_pct = (prices.iloc[-1] / prices.iloc[0] - 1) * 100
    daily_returns = prices.pct_change().dropna()
    volatility_pct = daily_returns.std() * (TRADING_DAYS_PER_YEAR**0.5) * 100 if len(daily_returns) > 1 else None
    return float(total_return_pct), (float(volatility_pct) if volatility_pct is not None else None)


def _buy_and_hold_value(prices: pd.DataFrame, tickers: list[str], initial_capital: float) -> pd.Series:
    weight = 1.0 / len(tickers)
    normalized = prices[tickers] / prices[tickers].iloc[0]
    return (normalized * weight).sum(axis=1) * initial_capital


def _periodic_rebalance_value(prices: pd.DataFrame, tickers: list[str], initial_capital: float, frequency: str) -> pd.Series:
    """Equal-weight, but weights are reset to 1/N at the start of each period instead of drifting
    for the whole run -- modeled by chaining independent buy & hold sub-curves per period, each
    one starting from the prior period's ending value. No transaction costs, same as buy & hold.
    """
    pandas_period = _REBALANCE_FREQUENCY_TO_PANDAS_PERIOD.get(frequency)
    if pandas_period is None:
        raise InvalidStrategyParamsError(f"Unknown rebalanceFrequency: {frequency}")

    weight = 1.0 / len(tickers)
    period_labels = pd.to_datetime(pd.Series(prices.index)).dt.to_period(pandas_period).to_numpy()

    values = pd.Series(index=prices.index, dtype=float)
    capital = initial_capital
    for period in pd.unique(period_labels):
        mask = period_labels == period
        period_prices = prices.loc[mask, tickers]
        normalized = period_prices / period_prices.iloc[0]
        period_value = (normalized * weight).sum(axis=1) * capital
        # price_history's DECIMAL columns come back as object-dtype Decimals (see _load_closes),
        # which survive elementwise arithmetic but must be cast before a bulk assignment into a
        # float64 Series -- otherwise pandas' setitem raises LossySetitemError.
        values.loc[mask] = period_value.to_numpy(dtype=float)
        capital = float(period_value.iloc[-1])
    return values


def _sma_crossover_value(
    prices: pd.DataFrame, tickers: list[str], initial_capital: float, short_window: int, long_window: int
) -> pd.Series:
    """Equal-weight, but each ticker only participates in the portfolio's return on days its
    short-window SMA is above its long-window SMA (golden cross), sitting in cash (0% that day)
    otherwise. Signals are shifted a day so today's allocation is decided by yesterday's close --
    computing the SMAs off today's own close and trading on it the same day would be lookahead bias.
    """
    if short_window <= 0 or long_window <= 0 or short_window >= long_window:
        raise InvalidStrategyParamsError("smaShortWindow must be a positive integer less than smaLongWindow")

    weight = 1.0 / len(tickers)
    daily_returns = prices[tickers].pct_change().fillna(0.0)
    short_sma = prices[tickers].rolling(short_window).mean()
    long_sma = prices[tickers].rolling(long_window).mean()
    in_position = (short_sma > long_sma).shift(1).fillna(False)
    masked_returns = daily_returns.where(in_position, 0.0)
    per_ticker_growth = (1.0 + masked_returns).cumprod()
    return (per_ticker_growth * weight).sum(axis=1) * initial_capital


def _volatility_target_value(
    prices: pd.DataFrame,
    tickers: list[str],
    initial_capital: float,
    target_volatility_pct: float,
    vix_threshold: float,
    vix_closes: pd.Series,
) -> pd.Series:
    """Equal-weight base portfolio, exposure scaled day to day to min(1, target vol / trailing
    realized vol) -- 100% exposure is the ceiling, no leverage. Exposure is gated to zero whenever
    SPY closes below its own 200-day MA (regime filter) or VIX is at/above vix_threshold (fear
    filter); the ungated portion sits in cash (0% that day). Every signal (trend, VIX, realized
    vol) lags a day so today's exposure is decided by yesterday's close, same no-lookahead
    reasoning as SMA_CROSSOVER.
    """
    if target_volatility_pct <= 0:
        raise InvalidStrategyParamsError("targetVolatilityPct must be positive")
    if BENCHMARK_TICKER not in prices.columns:
        raise InsufficientDataError(f"No price data for benchmark {BENCHMARK_TICKER}")

    weight = 1.0 / len(tickers)
    normalized = prices[tickers] / prices[tickers].iloc[0]
    base_index = (normalized * weight).sum(axis=1)
    base_returns = base_index.pct_change().fillna(0.0)

    spy = prices[BENCHMARK_TICKER]
    spy_trend_ma = spy.rolling(VOL_TARGET_TREND_MA_WINDOW).mean()
    trend_ok = (spy > spy_trend_ma).shift(1).fillna(False)

    vix_aligned = vix_closes.reindex(prices.index).ffill()
    vix_ok = (vix_aligned < vix_threshold).shift(1).fillna(False)

    realized_vol = base_returns.rolling(VOL_TARGET_VOL_LOOKBACK_WINDOW).std() * (TRADING_DAYS_PER_YEAR**0.5)
    target_vol = target_volatility_pct / 100.0
    raw_exposure = pd.Series(0.0, index=base_returns.index)
    has_vol = realized_vol > 0
    raw_exposure[has_vol] = (target_vol / realized_vol[has_vol]).clip(upper=1.0)
    exposure = raw_exposure.shift(1).fillna(0.0)
    exposure = exposure.where(trend_ok & vix_ok, 0.0)

    strategy_returns = exposure * base_returns
    return initial_capital * (1.0 + strategy_returns).cumprod()


def _portfolio_value_for_strategy(
    closes: pd.DataFrame,
    tickers: list[str],
    initial_capital: float,
    strategy_type: str,
    strategy_params: dict,
    vix_closes: pd.Series | None = None,
) -> pd.Series:
    if strategy_type == STRATEGY_SMA_CROSSOVER:
        short_window = strategy_params.get("smaShortWindow")
        long_window = strategy_params.get("smaLongWindow")
        if short_window is None or long_window is None:
            raise InvalidStrategyParamsError("SMA_CROSSOVER requires smaShortWindow and smaLongWindow")
        return _sma_crossover_value(closes, tickers, initial_capital, int(short_window), int(long_window))
    if strategy_type == STRATEGY_PERIODIC_REBALANCE:
        frequency = strategy_params.get("rebalanceFrequency")
        if frequency is None:
            raise InvalidStrategyParamsError("PERIODIC_REBALANCE requires rebalanceFrequency")
        return _periodic_rebalance_value(closes, tickers, initial_capital, frequency)
    if strategy_type == STRATEGY_VOLATILITY_TARGET:
        target_volatility_pct = strategy_params.get("targetVolatilityPct")
        vix_threshold = strategy_params.get("vixThreshold")
        if target_volatility_pct is None or vix_threshold is None:
            raise InvalidStrategyParamsError("VOLATILITY_TARGET requires targetVolatilityPct and vixThreshold")
        if vix_closes is None:
            raise InvalidStrategyParamsError("VOLATILITY_TARGET requires VIX data")
        return _volatility_target_value(
            closes, tickers, initial_capital, float(target_volatility_pct), float(vix_threshold), vix_closes
        )
    if strategy_type != STRATEGY_BUY_AND_HOLD:
        raise InvalidStrategyParamsError(f"Unknown strategyType: {strategy_type}")
    return _buy_and_hold_value(closes, tickers, initial_capital)


def _compute_backtest(
    closes: pd.DataFrame,
    tickers: list[str],
    initial_capital: float,
    risk_free_rate: float,
    strategy_type: str = STRATEGY_BUY_AND_HOLD,
    strategy_params: dict | None = None,
    vix_closes: pd.Series | None = None,
) -> dict:
    """Pure: given a wide closes DataFrame (must already include `tickers` and BENCHMARK_TICKER
    columns), computes the selected strategy's equity curve vs the benchmark, plus summary
    risk/return metrics. Fractional shares assumed -- this is a valuation model, not an order book.
    """
    portfolio_tickers = [t for t in tickers if t in closes.columns]
    if not portfolio_tickers:
        raise InsufficientDataError("None of the requested tickers have price data in range")
    if BENCHMARK_TICKER not in closes.columns:
        raise InsufficientDataError(f"No price data for benchmark {BENCHMARK_TICKER}")

    portfolio_value = _portfolio_value_for_strategy(
        closes, portfolio_tickers, initial_capital, strategy_type, strategy_params or {}, vix_closes
    )
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

    ticker_stats = []
    for t in portfolio_tickers:
        r, v = _return_and_volatility(closes[t])
        ticker_stats.append({"ticker": t, "returnPct": round(r, 2), "volatilityPct": round(v, 2) if v is not None else None})

    benchmark_return_pct, benchmark_volatility_pct = _return_and_volatility(closes[BENCHMARK_TICKER])

    return {
        "equityCurve": equity_curve,
        "metrics": {
            "totalReturnPct": round(float(total_return_pct), 2),
            "cagrPct": round(float(cagr_pct), 2) if cagr_pct is not None else None,
            "maxDrawdownPct": round(float(max_drawdown_pct), 2),
            "volatilityPct": round(float(volatility_pct), 2) if volatility_pct is not None else None,
            "sharpeRatio": round(float(sharpe_ratio), 2) if sharpe_ratio is not None else None,
        },
        "tickerStats": ticker_stats,
        "benchmarkStats": {
            "ticker": BENCHMARK_TICKER,
            "returnPct": round(benchmark_return_pct, 2),
            "volatilityPct": round(benchmark_volatility_pct, 2) if benchmark_volatility_pct is not None else None,
        },
    }


def run_backtest(
    tickers: list[str],
    start: date,
    end: date,
    initial_capital: float,
    risk_free_rate: float,
    strategy_type: str = STRATEGY_BUY_AND_HOLD,
    strategy_params: dict | None = None,
) -> dict:
    all_tickers = list(dict.fromkeys([*tickers, BENCHMARK_TICKER]))
    closes = _load_closes(all_tickers, start, end)
    vix_closes = _load_vix_closes(start, end) if strategy_type == STRATEGY_VOLATILITY_TARGET else None
    return _compute_backtest(closes, tickers, initial_capital, risk_free_rate, strategy_type, strategy_params, vix_closes)
