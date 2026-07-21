from datetime import date

import pandas as pd
import pytest

from app.backtest import BENCHMARK_TICKER, InsufficientDataError, _compute_backtest


def _closes(rows: dict[str, list[float]], start: date = date(2026, 1, 1)) -> pd.DataFrame:
    dates = pd.date_range(start, periods=len(next(iter(rows.values()))), freq="D").date
    return pd.DataFrame(rows, index=dates)


def test_equal_weight_buy_and_hold_matches_average_return():
    # AAA doubles, BBB stays flat -> equal-weight portfolio should be up 50% by the end.
    closes = _closes({"AAA": [100, 150, 200], "BBB": [50, 50, 50], BENCHMARK_TICKER: [10, 10, 10]})
    result = _compute_backtest(closes, ["AAA", "BBB"], initial_capital=1000.0, risk_free_rate=0.0)

    assert len(result["equityCurve"]) == 3
    assert result["equityCurve"][0]["portfolioValue"] == 1000.0
    assert result["metrics"]["totalReturnPct"] == pytest.approx(50.0, abs=0.01)


def test_flat_prices_have_zero_drawdown_and_return():
    closes = _closes({"AAA": [100, 100, 100], BENCHMARK_TICKER: [10, 10, 10]})
    result = _compute_backtest(closes, ["AAA"], initial_capital=1000.0, risk_free_rate=0.0)

    assert result["metrics"]["totalReturnPct"] == 0.0
    assert result["metrics"]["maxDrawdownPct"] == 0.0


def test_drawdown_is_negative_after_a_peak_and_drop():
    closes = _closes({"AAA": [100, 200, 100], BENCHMARK_TICKER: [10, 10, 10]})
    result = _compute_backtest(closes, ["AAA"], initial_capital=1000.0, risk_free_rate=0.0)

    # Peaks at 2x, then halves from the peak -> -50% drawdown.
    assert result["metrics"]["maxDrawdownPct"] == pytest.approx(-50.0, abs=0.01)


def test_missing_benchmark_raises():
    closes = _closes({"AAA": [100, 110]})
    with pytest.raises(InsufficientDataError):
        _compute_backtest(closes, ["AAA"], initial_capital=1000.0, risk_free_rate=0.0)


def test_no_requested_tickers_present_raises():
    closes = _closes({BENCHMARK_TICKER: [10, 10]})
    with pytest.raises(InsufficientDataError):
        _compute_backtest(closes, ["ZZZ"], initial_capital=1000.0, risk_free_rate=0.0)
