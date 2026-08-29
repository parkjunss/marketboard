import numpy as np
import pandas as pd
import pytest

from app.quant_analysis import (
    InvalidAnalysisParamsError,
    _beta_and_correlation,
    _drawdown_stats,
    _ewma_volatility_pct,
    _historical_var_cvar_pct,
    _hurst_exponent,
    _hurst_interpretation,
    _monte_carlo_percentiles,
    analyze_stock,
)


def test_ewma_volatility_ranks_a_choppier_series_higher():
    calm = pd.Series([0.001, -0.001] * 30)
    choppy = pd.Series([0.05, -0.05] * 30)

    calm_vol = _ewma_volatility_pct(calm)
    choppy_vol = _ewma_volatility_pct(choppy)

    assert calm_vol is not None and choppy_vol is not None
    assert choppy_vol > calm_vol


def test_ewma_volatility_none_for_too_short_a_series():
    assert _ewma_volatility_pct(pd.Series([0.01])) is None


def test_historical_var_cvar_matches_hand_computed_order_statistics():
    # 100 evenly-spaced returns from -0.100 to -0.001 (step 0.001), already sorted ascending.
    # At 95% confidence, cutoff_idx = round(0.05*100) = 5 -> the 5th-smallest return (index 4)
    # is -0.096 (VaR=9.6%), and the mean of the 5 smallest (-0.100..-0.096) is -0.098 (CVaR=9.8%).
    returns = pd.Series([-0.10 + i * 0.001 for i in range(100)])

    var_pct, cvar_pct = _historical_var_cvar_pct(returns, confidence=0.95)

    assert var_pct == pytest.approx(9.6, abs=0.01)
    assert cvar_pct == pytest.approx(9.8, abs=0.01)


def test_historical_var_cvar_none_for_too_short_a_series():
    assert _historical_var_cvar_pct(pd.Series([0.01, -0.02]), confidence=0.95) == (None, None)


def test_hurst_exponent_distinguishes_trending_from_mean_reverting():
    n = 500
    t = np.arange(n)
    # Sine wave oscillating around a fixed level -- strongly mean-reverting.
    mean_reverting_prices = pd.Series(100 + 5 * np.sin(t * 0.3))
    # Smooth compounding growth with a touch of noise -- strongly trending.
    rng = np.random.default_rng(42)
    trending_prices = pd.Series(100 * np.cumprod(1 + 0.001 + 0.0001 * rng.standard_normal(n)))

    h_mean_reverting = _hurst_exponent(mean_reverting_prices)
    h_trending = _hurst_exponent(trending_prices)

    assert h_mean_reverting is not None and h_mean_reverting < 0.45
    assert h_trending is not None and h_trending > 0.55
    assert _hurst_interpretation(h_mean_reverting) == "MEAN_REVERTING"
    assert _hurst_interpretation(h_trending) == "TRENDING"


def test_hurst_exponent_none_for_too_short_a_series():
    assert _hurst_exponent(pd.Series(range(50), dtype=float) + 100) is None


def test_hurst_exponent_handles_object_dtype_prices():
    # Regression test: price_history-backed closes arrive as object-dtype columns (see
    # backtest.py's _load_closes docstring) even though every value is a plain float --
    # np.log() on an object array previously crashed with "float object has no attribute log"
    # because it tries to call .log() per element instead of vectorizing.
    n = 500
    rng = np.random.default_rng(7)
    prices = pd.Series(100 * np.cumprod(1 + 0.0005 + 0.001 * rng.standard_normal(n)), dtype=object)

    assert _hurst_exponent(prices) is not None


def test_hurst_interpretation_random_walk_band():
    assert _hurst_interpretation(0.5) == "RANDOM_WALK"
    assert _hurst_interpretation(None) is None


def test_drawdown_stats_peak_trough_and_duration():
    # Peaks at 200 (idx 1), troughs at 50 (idx 4) before a new peak at 300 (idx 5) --
    # -75% drawdown, and 3 consecutive days (idx 2,3,4) spent below the prior peak.
    prices = pd.Series([100.0, 200.0, 100.0, 150.0, 50.0, 300.0])

    stats = _drawdown_stats(prices)

    assert stats["maxDrawdownPct"] == pytest.approx(-75.0, abs=0.01)
    assert stats["maxDrawdownDurationDays"] == 3


def test_beta_and_correlation_for_a_perfectly_levered_relationship():
    # stock is exactly 2x the benchmark's daily returns -> beta=2.0, correlation=1.0.
    benchmark_returns = pd.Series([0.01, -0.02, 0.03, -0.01, 0.02] * 4)
    stock_returns = benchmark_returns * 2

    beta, correlation = _beta_and_correlation(stock_returns, benchmark_returns)

    assert beta == pytest.approx(2.0, abs=0.001)
    assert correlation == pytest.approx(1.0, abs=0.001)


def test_beta_and_correlation_none_for_too_short_a_series():
    assert _beta_and_correlation(pd.Series([0.01]), pd.Series([0.02])) == (None, None)


def test_monte_carlo_percentiles_are_ordered_and_correctly_shaped():
    result = _monte_carlo_percentiles(last_price=100.0, mu_daily=0.0005, sigma_daily=0.02, horizon_days=10, num_paths=500)

    assert set(result.keys()) == {"p5", "p25", "p50", "p75", "p95"}
    for series in result.values():
        assert len(series) == 10
    # Percentile bands must be non-decreasing across p5 -> p95 for every simulated day.
    for day in range(10):
        values = [result[f"p{p}"][day] for p in (5, 25, 50, 75, 95)]
        assert values == sorted(values)


def test_analyze_stock_rejects_invalid_params():
    with pytest.raises(InvalidAnalysisParamsError):
        analyze_stock("AAPL", lookback_days=10)
    with pytest.raises(InvalidAnalysisParamsError):
        analyze_stock("AAPL", monte_carlo_horizon_days=0)
    with pytest.raises(InvalidAnalysisParamsError):
        analyze_stock("AAPL", monte_carlo_paths=1)
