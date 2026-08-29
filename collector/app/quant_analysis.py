"""Single-stock quantitative analysis -- volatility, historical VaR/CVaR, return-distribution
shape, a Hurst-exponent trend/mean-reversion read, drawdown, beta/correlation vs a benchmark, and
a Monte Carlo GBM price-path projection. All DB-only (price_history), same "no fresh yfinance
calls" reasoning as backtest.py -- reuses its _load_closes rather than re-implementing the same
symbol-id-first query pattern a third time (see screener.py's own copy for the S&P-500-universe
case, which needed different dropna semantics; this one is the plain 2-ticker case backtest.py
already handles).

Formulas/methods sourced from FinMathematics-master (see PROGRESS.md for the reading pass):
- EWMA volatility: RiskMetrics recursion sigma_t^2 = lambda*sigma_{t-1}^2 + (1-lambda)*r_{t-1}^2
  (Applied Quantitative Finance, ch.1)
- Historical VaR/CVaR: order-statistic method, no distributional assumption
  (Quantitative Finance for Physicists, ch.11)
- Hurst exponent via rescaled-range (R/S) analysis (Applied Quantitative Finance, ch.14)
"""

from datetime import date, timedelta

import numpy as np
import pandas as pd

from .backtest import InsufficientDataError, _load_closes

BENCHMARK_TICKER = "SPY"
TRADING_DAYS_PER_YEAR = 252
EWMA_LAMBDA = 0.94  # RiskMetrics standard daily decay factor
VAR_CONFIDENCE_LEVELS = (0.95, 0.99)
HURST_TRENDING_THRESHOLD = 0.55
HURST_MEAN_REVERTING_THRESHOLD = 0.45

DEFAULT_LOOKBACK_DAYS = 504  # ~2 trading years
MIN_LOOKBACK_DAYS = 60
MAX_LOOKBACK_DAYS = 1260  # ~5 trading years

DEFAULT_MONTE_CARLO_HORIZON_DAYS = 252
MIN_MONTE_CARLO_HORIZON_DAYS = 5
MAX_MONTE_CARLO_HORIZON_DAYS = 756  # ~3 trading years

DEFAULT_MONTE_CARLO_PATHS = 2000
MIN_MONTE_CARLO_PATHS = 100
MAX_MONTE_CARLO_PATHS = 5000

MONTE_CARLO_PERCENTILES = (5, 25, 50, 75, 95)


class InvalidAnalysisParamsError(Exception):
    pass


def _ewma_volatility_pct(returns: pd.Series, lam: float = EWMA_LAMBDA) -> float | None:
    """RiskMetrics-style EWMA volatility, annualized. Zero-mean assumption (standard for this
    estimator, and immaterial at daily granularity where mean return is tiny next to variance).
    """
    if len(returns) < 2:
        return None
    ewma_var = returns.pow(2).ewm(alpha=1 - lam, adjust=False).mean()
    return float(ewma_var.iloc[-1] ** 0.5 * (TRADING_DAYS_PER_YEAR**0.5) * 100)


def _historical_var_cvar_pct(returns: pd.Series, confidence: float) -> tuple[float | None, float | None]:
    """Historical (order-statistic) VaR/CVaR -- no distributional assumption. Both reported as
    positive loss percentages (e.g. 3.2 means "a 3.2% one-day loss"), not signed returns.
    """
    if len(returns) < 20:
        return None, None
    sorted_returns = returns.sort_values()
    cutoff_idx = max(int(round((1 - confidence) * len(sorted_returns))), 1)
    var_return = sorted_returns.iloc[cutoff_idx - 1]
    cvar_return = sorted_returns.iloc[:cutoff_idx].mean()
    return float(-var_return * 100), float(-cvar_return * 100)


def _hurst_exponent(prices: pd.Series) -> float | None:
    """Rescaled-range (R/S) Hurst exponent from log prices. H > 0.5 => trending/persistent,
    H < 0.5 => mean-reverting, H ~= 0.5 => random walk. Needs a reasonable amount of history to
    get enough (lag, R/S) points for a stable regression slope.
    """
    # price_history columns come back as object dtype (see backtest.py's _load_closes docstring
    # history) -- np.log() on an object array tries to call .log() per element instead of
    # vectorizing, which fails for both float and Decimal. Cast to float64 first.
    log_prices = np.log(prices.to_numpy(dtype=float))
    n = len(log_prices)
    if n < 100:
        return None

    candidate_lags = np.unique(np.logspace(np.log10(10), np.log10(n // 2), num=20).astype(int))
    rs_means: list[float] = []
    valid_lags: list[int] = []
    for lag in candidate_lags:
        chunk_count = n // lag
        if chunk_count < 1:
            continue
        rs_per_chunk = []
        for i in range(chunk_count):
            segment = log_prices[i * lag : (i + 1) * lag]
            if len(segment) < 2:
                continue
            deviations = segment - segment.mean()
            cumulative = np.cumsum(deviations)
            spread = cumulative.max() - cumulative.min()
            std = segment.std()
            if std > 0:
                rs_per_chunk.append(spread / std)
        if rs_per_chunk:
            rs_means.append(float(np.mean(rs_per_chunk)))
            valid_lags.append(lag)

    if len(valid_lags) < 5:
        return None
    slope, _ = np.polyfit(np.log(valid_lags), np.log(rs_means), 1)
    return float(slope)


def _hurst_interpretation(hurst: float | None) -> str | None:
    if hurst is None:
        return None
    if hurst > HURST_TRENDING_THRESHOLD:
        return "TRENDING"
    if hurst < HURST_MEAN_REVERTING_THRESHOLD:
        return "MEAN_REVERTING"
    return "RANDOM_WALK"


def _drawdown_stats(prices: pd.Series) -> dict:
    running_max = prices.cummax()
    drawdown = (prices - running_max) / running_max
    max_drawdown_pct = float(drawdown.min() * 100)

    max_duration = 0
    current = 0
    for below_peak in prices < running_max:
        if below_peak:
            current += 1
            max_duration = max(max_duration, current)
        else:
            current = 0

    return {"maxDrawdownPct": round(max_drawdown_pct, 2), "maxDrawdownDurationDays": max_duration}


def _beta_and_correlation(stock_returns: pd.Series, benchmark_returns: pd.Series) -> tuple[float | None, float | None]:
    aligned = pd.concat([stock_returns, benchmark_returns], axis=1).dropna()
    if len(aligned) < 20:
        return None, None
    stock_col, benchmark_col = aligned.iloc[:, 0], aligned.iloc[:, 1]
    benchmark_var = benchmark_col.var()
    beta = float(stock_col.cov(benchmark_col) / benchmark_var) if benchmark_var > 0 else None
    correlation = float(stock_col.corr(benchmark_col))
    return beta, correlation


def _monte_carlo_percentiles(
    last_price: float, mu_daily: float, sigma_daily: float, horizon_days: int, num_paths: int
) -> dict[str, list[float]]:
    """Geometric Brownian motion price paths simulated from historical daily log-return mean/std,
    summarized as percentile bands per day (a fan chart) rather than returning every path.
    """
    rng = np.random.default_rng()
    shocks = rng.standard_normal((num_paths, horizon_days))
    daily_log_returns = (mu_daily - 0.5 * sigma_daily**2) + sigma_daily * shocks
    cumulative_log_returns = np.cumsum(daily_log_returns, axis=1)
    price_paths = last_price * np.exp(cumulative_log_returns)

    return {f"p{p}": np.round(np.percentile(price_paths, p, axis=0), 2).tolist() for p in MONTE_CARLO_PERCENTILES}


def analyze_stock(
    ticker: str,
    lookback_days: int = DEFAULT_LOOKBACK_DAYS,
    monte_carlo_horizon_days: int = DEFAULT_MONTE_CARLO_HORIZON_DAYS,
    monte_carlo_paths: int = DEFAULT_MONTE_CARLO_PATHS,
) -> dict:
    if not (MIN_LOOKBACK_DAYS <= lookback_days <= MAX_LOOKBACK_DAYS):
        raise InvalidAnalysisParamsError(f"lookbackDays must be between {MIN_LOOKBACK_DAYS} and {MAX_LOOKBACK_DAYS}")
    if not (MIN_MONTE_CARLO_HORIZON_DAYS <= monte_carlo_horizon_days <= MAX_MONTE_CARLO_HORIZON_DAYS):
        raise InvalidAnalysisParamsError(
            f"monteCarloHorizonDays must be between {MIN_MONTE_CARLO_HORIZON_DAYS} and {MAX_MONTE_CARLO_HORIZON_DAYS}"
        )
    if not (MIN_MONTE_CARLO_PATHS <= monte_carlo_paths <= MAX_MONTE_CARLO_PATHS):
        raise InvalidAnalysisParamsError(f"monteCarloPaths must be between {MIN_MONTE_CARLO_PATHS} and {MAX_MONTE_CARLO_PATHS}")

    ticker = ticker.upper()
    end = date.today()
    start = end - timedelta(days=int(lookback_days * 1.6))  # calendar-day pad for weekends/holidays
    tickers = [ticker] if ticker == BENCHMARK_TICKER else [ticker, BENCHMARK_TICKER]
    closes = _load_closes(tickers, start, end)
    if ticker not in closes.columns:
        raise InsufficientDataError(f"No price data for {ticker}")

    stock_prices = closes[ticker].tail(lookback_days)
    stock_returns = stock_prices.pct_change().dropna()

    var_cvar = {}
    for confidence in VAR_CONFIDENCE_LEVELS:
        var_pct, cvar_pct = _historical_var_cvar_pct(stock_returns, confidence)
        key = str(int(confidence * 100))
        var_cvar[key] = {
            "varPct": round(var_pct, 2) if var_pct is not None else None,
            "cvarPct": round(cvar_pct, 2) if cvar_pct is not None else None,
        }

    hurst = _hurst_exponent(stock_prices)

    if ticker == BENCHMARK_TICKER:
        beta, correlation = 1.0, 1.0
    else:
        benchmark_returns = closes[BENCHMARK_TICKER].tail(lookback_days).pct_change().dropna()
        beta, correlation = _beta_and_correlation(stock_returns, benchmark_returns)

    volatility_pct = _ewma_volatility_pct(stock_returns)
    mu_daily = float(stock_returns.mean())
    sigma_daily = float(stock_returns.std())
    last_price = float(stock_prices.iloc[-1])

    return {
        "ticker": ticker,
        "asOfDate": stock_prices.index[-1].isoformat(),
        "lastPrice": round(last_price, 2),
        "lookbackDays": lookback_days,
        "volatility": {"annualizedPct": round(volatility_pct, 2) if volatility_pct is not None else None},
        "risk": var_cvar,
        "distribution": {
            "skewness": round(float(stock_returns.skew()), 3) if len(stock_returns) >= 20 else None,
            "excessKurtosis": round(float(stock_returns.kurtosis()), 3) if len(stock_returns) >= 20 else None,
        },
        "hurstExponent": round(hurst, 3) if hurst is not None else None,
        "hurstInterpretation": _hurst_interpretation(hurst),
        "drawdown": _drawdown_stats(stock_prices),
        "benchmark": {
            "ticker": BENCHMARK_TICKER,
            "beta": round(beta, 3) if beta is not None else None,
            "correlation": round(correlation, 3) if correlation is not None else None,
        },
        "monteCarlo": {
            "horizonDays": monte_carlo_horizon_days,
            "paths": monte_carlo_paths,
            "percentiles": _monte_carlo_percentiles(last_price, mu_daily, sigma_daily, monte_carlo_horizon_days, monte_carlo_paths),
        },
    }
