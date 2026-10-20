from copy import deepcopy
from decimal import Decimal
import pytest
from app.auto_trading import replay_auto, compare_auto

CONFIG = dict(version="sma-paper-v1", window=2, initialCapital="10000", feeBps="5",
              slippageBps="10", maxPositionWeight="0.8", maxOrderValue="5000")
BARS = [dict(date=f"2026-01-{i+1:02}", open=str(p), close=str(p))
        for i, p in enumerate([100, 110, 120, 90, 80, 100, 110])]

def test_comparison_matches_entry_costs_and_keeps_terminal_position():
    result = compare_auto(CONFIG, BARS)
    entry = replay_auto(CONFIG, BARS)["entries"][2]
    benchmark = result["benchmark"]
    assert benchmark["shares"] == entry["quantity"]
    assert benchmark["fees"] == entry["fee"]
    assert benchmark["trades"] == 1
    assert result["entryDate"] == BARS[2]["date"]
    assert Decimal(benchmark["equity"]) == Decimal(benchmark["cash"]) + Decimal(benchmark["shares"]) * 110
    assert Decimal(benchmark["maxDrawdownPct"]) > 0
    assert result["strategy"]["trades"] == 3
    assert result["priceQuality"]["adjustment"] == "UNKNOWN"


def test_comparison_warmup_and_prefix_do_not_use_future_prices():
    for size in (0, 1, 2):
        result = compare_auto(CONFIG, BARS[:size])
        assert result["benchmark"]["trades"] == 0
        assert Decimal(result["benchmark"]["equity"]) == 10000
        assert result["entryDate"] is None
    prefix = compare_auto(CONFIG, BARS[:3])
    assert compare_auto(CONFIG, BARS)["curve"][:3] == prefix["curve"]
    changed = deepcopy(BARS[:3])
    changed[-1]["close"] = "999"
    actual = compare_auto(CONFIG, changed)["benchmark"]
    for key in ("shares", "cash", "fees", "trades"):
        assert actual[key] == prefix["benchmark"][key]


def test_flat_prices_without_costs_have_zero_return_and_drawdown():
    bars = [dict(b, open="100", close="100") for b in BARS]
    result = compare_auto({**CONFIG, "feeBps": "0", "slippageBps": "0"}, bars)
    for key in ("strategy", "benchmark"):
        assert Decimal(result[key]["returnPct"]) == 0
        assert Decimal(result[key]["maxDrawdownPct"]) == 0


def test_validation_warmup_only_supplies_past_signals_and_resets_capital():
    warmup, evaluation = BARS[:2], BARS[2:]
    result = replay_auto(CONFIG, evaluation, warmup)
    full = replay_auto(CONFIG, BARS)
    assert len(result["entries"]) == len(evaluation)
    assert result["entries"][0]["step"] == 1
    assert result["entries"][0]["signalDate"] == warmup[-1]["date"]
    assert result["entries"][0]["action"] == "BUY"
    assert result["state"] == full["state"]
    comparison = compare_auto(CONFIG, evaluation, warmup)
    assert comparison["steps"] == len(evaluation)
    assert comparison["entryDate"] == evaluation[0]["date"]
    assert comparison["curve"][0]["date"] == evaluation[0]["date"]
    assert comparison["benchmark"]["trades"] == 1
    assert comparison["benchmark"]["shares"] == result["entries"][0]["quantity"]
    assert Decimal(replay_auto(CONFIG, [], warmup)["state"]["equity"]) == 10000
    assert compare_auto(CONFIG, evaluation[:1], warmup)["curve"] == comparison["curve"][:1]


def test_validation_first_fill_does_not_use_current_close_or_future_bars():
    expected = replay_auto(CONFIG, BARS[2:3], BARS[:2])["entries"][0]
    changed = deepcopy(BARS[2:])
    changed[0]["close"] = "999"
    changed[1]["open"] = "999"
    actual = replay_auto(CONFIG, changed, BARS[:2])["entries"][0]
    for field in ("action", "quantity", "fillPrice", "fee", "movingAverage"):
        assert actual[field] == expected[field]


@pytest.mark.parametrize("warmup", [BARS[:1], BARS[1:3], BARS[:2][::-1], [BARS[0], {**BARS[1], "close": "0"}]])
def test_invalid_or_overlapping_validation_warmup_rejected(warmup):
    with pytest.raises(ValueError):
        compare_auto(CONFIG, BARS[2:], warmup)



def test_signal_uses_previous_close_and_fills_next_open():
    result = replay_auto(CONFIG, BARS)
    entry = result["entries"][2]
    assert entry["action"] == "BUY"
    assert entry["signalDate"] == "2026-01-02"
    assert entry["movingAverage"] == "105.00000000"
    assert entry["fillPrice"] == "120.12000000"
    assert result["entries"][4]["action"] == "SELL"
    assert result["entries"][6]["action"] == "BUY"
    assert Decimal(result["state"]["cash"]) >= 0


def test_future_prices_and_current_close_cannot_change_current_fill():
    expected = replay_auto(CONFIG, BARS[:3])["entries"][2]
    changed = deepcopy(BARS)
    changed[2]["close"] = "900"
    changed[3]["open"] = "1000"
    actual = replay_auto(CONFIG, changed)["entries"][2]
    for field in ["action", "quantity", "fillPrice", "fee", "movingAverage"]:
        assert actual[field] == expected[field]
    assert replay_auto(CONFIG, BARS)["entries"][:3] == replay_auto(CONFIG, BARS[:3])["entries"]


def test_fees_and_pnl_conserve_equity_and_replay_is_deterministic():
    a = replay_auto(CONFIG, BARS)
    assert a == replay_auto(CONFIG, BARS)
    assert Decimal(a["state"]["fees"]) > 0
    for e in a["entries"]:
        s = e["state"]
        assert Decimal(s["equity"]) == Decimal("10000") + Decimal(s["realizedPnl"]) + Decimal(s["unrealizedPnl"])
        assert Decimal(e["quantity"]) * Decimal(e["fillPrice"] or "0") <= Decimal("5000")


@pytest.mark.parametrize("bars", [BARS[::-1], [BARS[0], BARS[0]], [dict(date="2026-01-01", open="0", close="100")]])
def test_invalid_prices_are_rejected(bars):
    with pytest.raises(ValueError):
        replay_auto(CONFIG, bars)


@pytest.mark.parametrize("field,value", [("window", 1), ("feeBps", "101"), ("maxPositionWeight", "1.1"), ("version", "future")])
def test_invalid_strategy_is_rejected(field, value):
    with pytest.raises(ValueError):
        replay_auto({**CONFIG, field: value}, BARS)
