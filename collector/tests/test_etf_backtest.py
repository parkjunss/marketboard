from copy import deepcopy
from datetime import date, datetime, timedelta
from math import prod

import pytest

from app import etf_backtest as engine
from app.backtest import InsufficientDataError
from app.etf_momentum import ETF_UNIVERSE, NEW_YORK

START, END = date(2024, 10, 1), date(2024, 11, 29)


@pytest.fixture
def bars():
    result = {ticker: [] for ticker in ETF_UNIVERSE}
    day = date(2023, 8, 1)
    while day <= date(2024, 12, 31):
        if day.weekday() < 5:
            for index, ticker in enumerate(ETF_UNIVERSE):
                close = 100 + len(result[ticker]) * (6 - index) / 100
                result[ticker].append((datetime(day.year, day.month, day.day, tzinfo=NEW_YORK), close, close))
        day += timedelta(days=1)
    return result


def compare(bars, fee=0, slippage=0, start=START, end=END):
    return engine.compare_etf_strategies(bars, start, end, 10_000, fee, slippage)


def test_same_period_next_session_and_shared_signal(bars):
    result = compare(bars)
    assert result["startDate"] == "2024-10-01"
    assert result["endDate"] == "2024-11-29"
    curves = [strategy["equityCurve"] for strategy in result["strategies"]]
    assert all([point["date"] for point in curve] == [point["date"] for point in curves[0]] for curve in curves)
    for strategy in result["strategies"]:
        assert all(trade["signalDate"] < trade["executionDate"] for trade in strategy["trades"])
    for strategy in result["strategies"][1:]:
        assert [(r["signalDate"], r["executionDate"]) for r in strategy["rebalances"]] == [
            ("2024-09-30", "2024-10-01"), ("2024-10-31", "2024-11-01")]
        selected = [t["ticker"] for t in strategy["rebalances"][0]["targets"] if t["weight"] > 0]
        assert selected == ["SPY", "QQQ", "EFA"]


def test_constant_prices_charge_first_fill_cost_without_negative_cash(bars):
    constant = {ticker: [(ts, 100, 100) for ts, _, _ in values] for ticker, values in bars.items()}
    result = compare(constant, fee=100, slippage=100)
    for strategy in result["strategies"][:2]:
        assert strategy["equityCurve"][-1]["value"] == pytest.approx(10_000 / 1.02, abs=0.01)
        assert strategy["metrics"]["totalReturnPct"] == -1.96
        assert strategy["metrics"]["maxDrawdownPct"] == -1.96
        assert strategy["totalFees"] == 98.04
        assert strategy["totalSlippage"] == 98.04
        assert strategy["turnoverPct"] == 98.04
    assert len(result["strategies"][1]["trades"]) == 3  # No needless second-month round trip.
    trend = result["strategies"][2]
    assert trend["trades"] == []
    assert trend["metrics"]["totalReturnPct"] == 0
    assert trend["metrics"]["sharpeRatio"] is None
    assert all(r["cashWeight"] == 1 for r in trend["rebalances"])


def test_first_fill_uses_open_not_signal_close_or_same_day_close(bars):
    for ticker, values in bars.items():
        bars[ticker] = [(ts, 200 if ts.date() == START else opening,
                        220 if ts.date() == START else close) for ts, opening, close in values]
    result = compare(bars)
    spy = result["strategies"][0]
    assert spy["trades"][0]["price"] == 200
    assert spy["trades"][0]["quantity"] == pytest.approx(50)
    assert spy["equityCurve"][0]["value"] == 11_000
    assert spy["equityCurve"][0]["returnPct"] == 10


def test_future_prices_cannot_change_earlier_signals_or_trades(bars):
    before = compare(bars)
    modified = deepcopy(bars)
    for ticker, values in modified.items():
        modified[ticker] = [(ts, opening * 3 if ts.date() >= date(2024, 11, 1) else opening,
                            close * 3 if ts.date() >= date(2024, 11, 1) else close) for ts, opening, close in values]
    after = compare(modified)
    for old, new in zip(before["strategies"], after["strategies"]):
        assert old["rebalances"] == new["rebalances"]
        assert old["equityCurve"][:23] == new["equityCurve"][:23]
        assert [t for t in old["trades"] if t["executionDate"] < "2024-11-01"] == [
            t for t in new["trades"] if t["executionDate"] < "2024-11-01"]


def test_bars_after_requested_end_do_not_change_result(bars):
    before = compare(bars)
    for ticker, values in bars.items():
        bars[ticker] = [(ts, float("nan") if ts.date() > END else opening, close) for ts, opening, close in values]
    assert compare(bars) == before


def test_midmonth_start_and_month_end_never_execute_a_new_signal_early(bars):
    result = compare(bars, start=date(2024, 10, 15), end=date(2024, 10, 31))
    for strategy in result["strategies"]:
        assert len(strategy["rebalances"]) == 1
        assert strategy["rebalances"][0]["executionDate"] == "2024-10-15"
        assert strategy["rebalances"][0]["signalDate"] == "2024-09-30"


def test_trade_ledger_reconstructs_daily_nav_including_costs_and_overnight_gaps(bars):
    bars["SPY"] = [(ts, opening * 1.2 if ts.date() >= date(2024, 11, 1) else opening,
                    close * 1.25 if ts.date() >= date(2024, 11, 1) else close) for ts, opening, close in bars["SPY"]]
    prices = {ticker: {ts.date().isoformat(): close for ts, _, close in values} for ticker, values in bars.items()}
    result = compare(bars, fee=10, slippage=20)
    for strategy in result["strategies"]:
        holdings = dict.fromkeys(ETF_UNIVERSE, 0.0)
        cash = 10_000
        for point in strategy["equityCurve"]:
            for trade in strategy["trades"]:
                if trade["executionDate"] != point["date"]:
                    continue
                sign = 1 if trade["side"] == "BUY" else -1
                holdings[trade["ticker"]] += sign * trade["quantity"]
                cash -= sign * trade["notional"] + trade["fee"] + trade["slippage"]
            assert cash >= -1e-7
            assert all(quantity >= -1e-7 for quantity in holdings.values())
            nav = cash + sum(quantity * prices[ticker][point["date"]] for ticker, quantity in holdings.items())
            assert nav == pytest.approx(point["value"], abs=0.01)
        assert sum(regime["days"] for regime in strategy["regimes"]) == len(strategy["equityCurve"])
        regime_growth = prod(1 + r["returnPct"] / 100 for r in strategy["regimes"] if r["days"])
        assert regime_growth == pytest.approx(1 + strategy["metrics"]["totalReturnPct"] / 100, abs=0.0002)


@pytest.mark.parametrize("problem", ["missing", "warmup", "mismatch", "duplicate", "zero_open", "nan_close", "stale", "gap"])
def test_data_problem_stops_all_comparisons_instead_of_silently_shortening(bars, problem):
    if problem == "missing":
        del bars["GLD"]
    elif problem == "warmup":
        bars = {t: [bar for bar in values if bar[0].date() >= date(2024, 1, 1)] for t, values in bars.items()}
    elif problem == "mismatch":
        del bars["GLD"][100]
    elif problem == "duplicate":
        bars["GLD"].append(bars["GLD"][100])
    elif problem in ("zero_open", "nan_close"):
        ts, opening, close = bars["GLD"][100]
        bars["GLD"][100] = (ts, 0 if problem == "zero_open" else opening, float("nan") if problem == "nan_close" else close)
    elif problem == "stale":
        bars = {t: [bar for bar in values if bar[0].date() < date(2024, 11, 1)] for t, values in bars.items()}
    else:
        bars = {t: [bar for bar in values if not date(2024, 6, 1) <= bar[0].date() <= date(2024, 6, 15)] for t, values in bars.items()}
    with pytest.raises(InsufficientDataError):
        compare(bars)


@pytest.mark.parametrize("start,end,capital,fee,slip", [
    (END, START, 10_000, 5, 5), (date(2000, 1, 1), END, 10_000, 5, 5),
    (START, END, 0, 5, 5), (START, END, 10_000, -1, 5), (START, END, 10_000, 5, 101),
    (START, END, float("inf"), 5, 5), (START, END, 10_000, float("nan"), 5),
    (START, datetime.now(NEW_YORK).date(), 10_000, 5, 5),
])
def test_invalid_requests_are_rejected_before_loading(start, end, capital, fee, slip, monkeypatch):
    monkeypatch.setattr(engine, "load_daily_bars", lambda *args: pytest.fail("must not read DB"))
    with pytest.raises(ValueError):
        engine.run_etf_comparison(start, end, capital, fee, slip)


def test_run_loads_warmup_before_requested_period(monkeypatch, bars):
    def load(start, end):
        assert start == date(2023, 9, 23)
        assert end == END
        return bars
    monkeypatch.setattr(engine, "load_daily_bars", load)
    assert engine.run_etf_comparison(START, END, 10_000, 0, 0)["ready"]
