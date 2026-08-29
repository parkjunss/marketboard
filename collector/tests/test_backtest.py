from datetime import date

import pandas as pd
import pytest

from app.backtest import BENCHMARK_TICKER, InsufficientDataError, InvalidStrategyParamsError, _compute_backtest


def _closes(rows: dict[str, list[float]], start: date = date(2026, 1, 1)) -> pd.DataFrame:
    dates = pd.date_range(start, periods=len(next(iter(rows.values()))), freq="D").date
    return pd.DataFrame(rows, index=dates)


def _object_dtype_closes(rows: dict[str, list[float]], start: date = date(2026, 1, 1)) -> pd.DataFrame:
    """Mirrors what _load_closes actually hands to _compute_backtest in production: pivot_table's
    date-indexed pivot leaves price columns as object dtype even though every value is a plain
    Python float (verified against real price_history data) -- a plain float-list DataFrame (see
    _closes above) is float64 from the start and can't reproduce dtype bugs that only show up
    against that object-dtype shape (e.g. a bulk assignment into a pre-typed float64 Series
    raising pandas' LossySetitemError).
    """
    dates = pd.date_range(start, periods=len(next(iter(rows.values()))), freq="D").date
    return pd.DataFrame({k: [float(v) for v in vs] for k, vs in rows.items()}, index=dates, dtype=object)


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


def test_ticker_and_benchmark_stats_reflect_individual_returns():
    # AAA doubles, BBB stays flat, benchmark is up 20% -- per-ticker/benchmark stats should reflect
    # each series on its own, independent of the equal-weight portfolio blend.
    closes = _closes({"AAA": [100, 150, 200], "BBB": [50, 50, 50], BENCHMARK_TICKER: [10, 11, 12]})
    result = _compute_backtest(closes, ["AAA", "BBB"], initial_capital=1000.0, risk_free_rate=0.0)

    stats_by_ticker = {s["ticker"]: s for s in result["tickerStats"]}
    assert stats_by_ticker["AAA"]["returnPct"] == pytest.approx(100.0, abs=0.01)
    assert stats_by_ticker["BBB"]["returnPct"] == pytest.approx(0.0, abs=0.01)
    assert result["benchmarkStats"]["ticker"] == BENCHMARK_TICKER
    assert result["benchmarkStats"]["returnPct"] == pytest.approx(20.0, abs=0.01)


def test_sma_crossover_only_participates_after_a_lagged_golden_cross():
    # AAA is flat for 4 days (SMA warm-up, no signal), then jumps 100->200 on day 4 and 200->300 on
    # day 5. short(2)/long(3) SMA crosses golden on day 4's close, but the signal is used lagged
    # (acted on starting the next day) to avoid lookahead -- so only day 4->5's +50% return should
    # count; day 3->4's +100% jump should be sitting in cash and NOT count.
    closes = _closes({"AAA": [100, 100, 100, 100, 200, 300], BENCHMARK_TICKER: [10, 10, 10, 10, 10, 10]})
    result = _compute_backtest(
        closes,
        ["AAA"],
        initial_capital=1000.0,
        risk_free_rate=0.0,
        strategy_type="SMA_CROSSOVER",
        strategy_params={"smaShortWindow": 2, "smaLongWindow": 3},
    )

    assert result["metrics"]["totalReturnPct"] == pytest.approx(50.0, abs=0.01)


def test_periodic_rebalance_compounds_interim_gains_across_periods():
    # AAA doubles in January then sits flat in February; BBB does the opposite. A plain buy & hold
    # would end up +100% either way (both tickers end at 2x, order doesn't matter). Monthly
    # rebalancing locks in January's +50% blended gain before February even starts, so the final
    # return should compound to +125%, not +100%.
    closes = pd.DataFrame(
        {
            "AAA": [100.0, 200.0, 200.0, 200.0],
            "BBB": [100.0, 100.0, 100.0, 200.0],
            BENCHMARK_TICKER: [10.0, 10.0, 10.0, 10.0],
        },
        index=[date(2026, 1, 1), date(2026, 1, 31), date(2026, 2, 1), date(2026, 2, 28)],
    )
    result = _compute_backtest(
        closes,
        ["AAA", "BBB"],
        initial_capital=1000.0,
        risk_free_rate=0.0,
        strategy_type="PERIODIC_REBALANCE",
        strategy_params={"rebalanceFrequency": "MONTHLY"},
    )

    assert result["metrics"]["totalReturnPct"] == pytest.approx(125.0, abs=0.01)


def test_sma_crossover_missing_params_raises():
    closes = _closes({"AAA": [100, 110], BENCHMARK_TICKER: [10, 10]})
    with pytest.raises(InvalidStrategyParamsError):
        _compute_backtest(closes, ["AAA"], initial_capital=1000.0, risk_free_rate=0.0, strategy_type="SMA_CROSSOVER")


def test_unknown_strategy_type_raises():
    closes = _closes({"AAA": [100, 110], BENCHMARK_TICKER: [10, 10]})
    with pytest.raises(InvalidStrategyParamsError):
        _compute_backtest(closes, ["AAA"], initial_capital=1000.0, risk_free_rate=0.0, strategy_type="NOT_A_STRATEGY")


def _alternating_return_prices(n: int, start_price: float = 100.0) -> list[float]:
    # +1%/-0.8% alternating gives both a net upward drift (so the SPY-vs-its-own-MA200 trend
    # filter reads "uptrend" once warmed up) and non-zero day-to-day variance (so realized vol
    # isn't ~0, which a constant-rate geometric series would produce and make exposure meaningless).
    prices = [start_price]
    for i in range(1, n):
        ret = 0.01 if i % 2 == 0 else -0.008
        prices.append(prices[-1] * (1 + ret))
    return prices


def test_volatility_target_requires_params_and_vix_data():
    closes = _closes({"AAA": [100, 110], BENCHMARK_TICKER: [10, 10]})
    with pytest.raises(InvalidStrategyParamsError):
        _compute_backtest(closes, ["AAA"], initial_capital=1000.0, risk_free_rate=0.0, strategy_type="VOLATILITY_TARGET")
    with pytest.raises(InvalidStrategyParamsError):
        _compute_backtest(
            closes,
            ["AAA"],
            initial_capital=1000.0,
            risk_free_rate=0.0,
            strategy_type="VOLATILITY_TARGET",
            strategy_params={"targetVolatilityPct": 15.0, "vixThreshold": 35.0},
        )  # vix_closes not passed


def test_volatility_target_exits_to_cash_the_day_after_a_vix_spike():
    n = 260
    prices = _alternating_return_prices(n)
    closes = _object_dtype_closes({"AAA": prices, BENCHMARK_TICKER: prices})
    vix_values = [15.0] * n
    vix_values[250] = 50.0  # one emergency day, safely after the 200-day trend-MA warm-up
    vix_closes = pd.Series(vix_values, index=closes.index)

    result = _compute_backtest(
        closes,
        ["AAA"],
        initial_capital=1000.0,
        risk_free_rate=0.0,
        strategy_type="VOLATILITY_TARGET",
        strategy_params={"targetVolatilityPct": 15.0, "vixThreshold": 35.0},
        vix_closes=vix_closes,
    )
    curve = [p["portfolioValue"] for p in result["equityCurve"]]
    # The spike is only *seen* the next day (signals lag one day), so day 251 should be sitting
    # entirely in cash (flat), unlike every other day which has non-zero exposure and movement.
    assert curve[251] == pytest.approx(curve[250], abs=0.01)
    assert curve[250] != pytest.approx(curve[249], abs=0.01)
    assert curve[252] != pytest.approx(curve[251], abs=0.01)


@pytest.mark.parametrize(
    "strategy_type,strategy_params",
    [
        ("BUY_AND_HOLD", None),
        ("SMA_CROSSOVER", {"smaShortWindow": 2, "smaLongWindow": 3}),
        ("PERIODIC_REBALANCE", {"rebalanceFrequency": "MONTHLY"}),
    ],
)
def test_all_strategies_handle_object_dtype_prices(strategy_type, strategy_params):
    # Regression test: real price_history-backed closes arrive as object-dtype columns (see
    # _object_dtype_closes) even though every value is a plain float -- PERIODIC_REBALANCE
    # previously crashed with pandas' LossySetitemError against exactly this shape of data, which
    # a float64-native test fixture couldn't have caught.
    closes = _object_dtype_closes(
        {"AAA": [100, 150, 200, 210, 220, 230], "BBB": [50, 55, 50, 60, 65, 70], BENCHMARK_TICKER: [10] * 6}
    )
    result = _compute_backtest(
        closes,
        ["AAA", "BBB"],
        initial_capital=1000.0,
        risk_free_rate=0.0,
        strategy_type=strategy_type,
        strategy_params=strategy_params,
    )
    assert len(result["equityCurve"]) == 6
    assert isinstance(result["metrics"]["totalReturnPct"], float)
