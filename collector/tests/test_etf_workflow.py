from copy import deepcopy
from datetime import date, datetime, timedelta

import pytest

from app import etf_workflow as workflow
from app.backtest import InsufficientDataError
from app.etf_momentum import ETF_UNIVERSE, NEW_YORK


def signal(as_of="2024-09-30", reverse=False):
    tickers = list(reversed(ETF_UNIVERSE)) if reverse else ETF_UNIVERSE
    return {"ready": True, "asOf": as_of, "rows": [
        {"ticker": t, "rank": i + 1, "weight": 1 / 3 if i < 3 else 0}
        for i, t in enumerate(tickers)]}


def prices(start=date(2024, 10, 1), days=4, extra=()):
    return {t: [(datetime.combine(start + timedelta(days=i), datetime.min.time(), NEW_YORK), 100, 100)
                for i in range(days)] for t in (*ETF_UNIVERSE, *extra)}


def paper():
    return {"calculationVersion": workflow.VERSION, "strategyId": "MOMENTUM_TREND",
            "initialCapital": 10000, "feeBps": 5, "slippageBps": 5,
            "state": {"date": "2024-09-30", "cash": 10000, "shares": {}, "value": 10000, "returnPct": 0},
            "signals": [{"id": 1, "capturedAt": "2024-10-02T12:00:00Z", "signal": signal()}]}


def test_no_trade_on_or_before_capture_date_and_cash_conservation():
    request = paper()
    original = deepcopy(request)
    result = workflow.advance_paper(request, prices(), date(2024, 10, 5))
    assert request == original
    assert all(not e["trades"] for e in result["events"][:2])
    assert len(result["events"][2]["trades"]) == 3
    assert not result["events"][3]["trades"]
    costs = sum(t["fee"] + t["slippage"] for e in result["events"] for t in e["trades"])
    assert result["state"]["value"] + costs == pytest.approx(10000)
    assert result["state"]["cash"] >= 0
    assert result["state"]["returnPct"] < 0


def test_refresh_is_noop_for_already_valued_dates():
    request = paper()
    first = workflow.advance_paper(request, prices(), date(2024, 10, 5))
    request["state"] = first["state"]
    second = workflow.advance_paper(request, prices(), date(2024, 10, 5))
    assert second["events"] == []
    assert second["state"] == first["state"]


def test_no_signal_keeps_cash_and_missing_new_signal_keeps_holdings():
    request = paper()
    request["signals"] = []
    result = workflow.advance_paper(request, prices(), date(2024, 10, 5))
    assert all(not e["trades"] for e in result["events"])
    assert result["state"]["value"] == 10000
    assert result["state"]["date"] == "2024-10-04"


def test_new_captured_signal_switches_only_after_capture():
    request = paper()
    request["signals"][0]["capturedAt"] = "2024-09-30T12:00:00Z"
    request["signals"].append({"id": 2, "capturedAt": "2024-10-02T12:00:00Z", "signal": signal(reverse=True)})
    result = workflow.advance_paper(request, prices(), date(2024, 10, 5))
    assert result["events"][1]["state"]["signalId"] == 1
    assert result["events"][2]["state"]["signalId"] == 2
    assert result["state"]["shares"]["SPY"] == pytest.approx(0)
    assert result["state"]["shares"]["GLD"] > 0


@pytest.mark.parametrize("invalid", [-1, float("nan"), float("inf")])
def test_bad_cash_rejected(invalid):
    request = paper(); request["state"]["cash"] = invalid
    with pytest.raises(ValueError):
        workflow.advance_paper(request, prices(), date(2024, 10, 5))


def test_mismatched_dates_and_large_gap_rejected():
    bars = prices(); bars["GLD"].pop()
    with pytest.raises(InsufficientDataError):
        workflow.advance_paper(paper(), bars, date(2024, 10, 5))
    with pytest.raises(InsufficientDataError):
        workflow.advance_paper(paper(), prices(date(2024, 10, 15)), date(2024, 10, 20))


def proposal():
    return {"strategyId": "MOMENTUM_TREND", "cashUsd": 1000, "feeBps": 5, "slippageBps": 5,
            "holdings": [{"ticker": "AAPL", "quantity": 10, "version": 7}], "maxPositionWeight": .5}


def test_proposal_sells_outside_universe_without_mutating_holdings():
    request = proposal(); original = deepcopy(request)
    result = workflow.rebalance_proposal(request, signal(), prices(extra=("AAPL",)), date(2024, 10, 5))
    assert request == original
    apple = next(r for r in result["rows"] if r["ticker"] == "AAPL")
    assert apple["adjustmentQuantity"] == -10 and apple["version"] == 7
    assert result["proposedCash"] >= 0
    assert (sum(r["targetValue"] for r in result["rows"]) + result["proposedCash"]
            + result["totalFees"] + result["totalSlippage"]) == pytest.approx(2000)


def test_rule_conflict_stale_or_mismatched_price_withholds_proposal():
    request = proposal(); bars = prices(extra=("AAPL",))
    request["maxPositionWeight"] = .2
    with pytest.raises(InsufficientDataError):
        workflow.rebalance_proposal(request, signal(), bars, date(2024, 10, 5))
    request["maxPositionWeight"] = .5
    with pytest.raises(InsufficientDataError):
        workflow.rebalance_proposal(request, signal(), bars, date(2024, 10, 10))
    bars["AAPL"].pop()
    with pytest.raises(InsufficientDataError):
        workflow.rebalance_proposal(request, signal(), bars, date(2024, 10, 5))


def test_capture_freezes_input_values(monkeypatch):
    bars = prices()
    monkeypatch.setattr(workflow, "load_daily_bars", lambda *args: bars)
    monkeypatch.setattr(workflow, "compare_etf_strategies", lambda *args: {"ready": True})
    config = {"startDate": date(2024, 10, 1), "endDate": date(2024, 10, 4),
              "initialCapital": 10000, "feeBps": 5, "slippageBps": 5}
    captured = workflow.capture_research(config, "MOMENTUM_TREND")
    bars["SPY"][0] = (bars["SPY"][0][0], 500, 500)
    assert captured["inputBars"]["SPY"][0]["close"] == 100
    assert captured["calculationVersion"] == workflow.VERSION


def test_capture_reports_missing_data_without_server_outage(monkeypatch):
    monkeypatch.setattr(workflow, "load_daily_bars", lambda *args: {})
    config = {"startDate": date(2024, 10, 1), "endDate": date(2024, 10, 4),
              "initialCapital": 10000, "feeBps": 5, "slippageBps": 5}
    captured = workflow.capture_research(config, "MOMENTUM_TREND")
    assert captured["comparison"]["ready"] is False
    assert captured["inputBars"] == {}
