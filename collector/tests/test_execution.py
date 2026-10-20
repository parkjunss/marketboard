import copy
from decimal import Decimal
import pytest
from app.execution import replay, VERSION

CONFIG = dict(version=VERSION, initialCapital="10000", feeBps="5", maxOrderValue="5000", maxPositionWeight="0.8")


def cmd(key, operation, order="buy", quantity=None):
    return dict(key=key, operation=operation, orderId=order, quantity=quantity)


def buy():
    return [cmd("buy", "BUY", quantity="10"), cmd("dispatch", "DISPATCH")]


def test_partial_cancel_duplicate_reverse_and_cash_conservation():
    events = buy() + [cmd("partial", "PARTIAL_FILL", quantity="4"), cmd("cancel", "CANCEL")]
    before = replay(CONFIG, events)
    assert before["cash"] == "10000.00000000"
    assert before["reservedCash"] == "1000.50000000"
    normal = replay(CONFIG, events + [cmd("sync", "RECONCILE")])
    reverse = replay(CONFIG, events + [cmd("sync", "REVERSE")])
    duplicate = replay(CONFIG, events + [cmd("sync", "DUPLICATE")])
    assert normal["economicHash"] == reverse["economicHash"] == duplicate["economicHash"]
    assert normal["cash"] == "9599.80000000"
    assert normal["shares"] == "4.00000000"
    assert normal["reservedCash"] == "0.00000000"
    assert normal["orders"]["buy"]["status"] == "CANCELLED"
    assert len(normal["applied"]) == 3
    assert normal["pending"] == {}


def test_timeout_does_not_release_reservation_or_resubmit():
    events = buy() + [cmd("lost", "TIMEOUT"), cmd("retry-dispatch", "DISPATCH")]
    state = replay(CONFIG, events)
    assert state["orders"]["buy"]["status"] == "UNKNOWN"
    assert len(state["broker"]["buy"]["events"]) == 1
    assert state["reservedCash"] == "1000.50000000"
    events += [cmd("fill", "FILL"), cmd("recover", "RECONCILE")]
    assert replay(CONFIG, events)["shares"] == "10.00000000"
    assert replay(CONFIG, events + [cmd("recover-again", "DUPLICATE")])["cash"] == "8999.50000000"


def test_idempotent_command_and_conflict():
    events = buy()
    assert replay(CONFIG, events) == replay(CONFIG, events + [events[0]])
    with pytest.raises(ValueError, match="다른 요청"):
        replay(CONFIG, events + [cmd("buy", "BUY", quantity="20")])


def test_sell_cost_basis_and_reentry_block():
    events = buy() + [cmd("f", "FILL"), cmd("r", "RECONCILE"),
                     cmd("sell", "SELL", quantity="10"), cmd("sd", "DISPATCH", "sell"),
                     cmd("sf", "FILL", "sell"), cmd("sr", "RECONCILE", "sell"),
                     cmd("reenter", "BUY", quantity="1")]
    state = replay(CONFIG, events)
    assert state["cash"] == "9999.00000000"
    assert state["realizedPnl"] == "-1.00000000"
    assert state["costBasis"] == "0.00000000"
    assert state["reentryBlocked"]
    assert "reenter" not in state["orders"]
    assert state["log"][-1]["kind"] == "BLOCKED"


@pytest.mark.parametrize("operation,quantity,reason", [("STALE_BUY", "1", "신선도"), ("BUY", "60", "주문 금액"), ("SELL", "1", "수량 부족")])
def test_risk_blocks_are_recorded(operation, quantity, reason):
    state = replay(CONFIG, [cmd("risk", operation, quantity=quantity)])
    assert not state["orders"]
    assert reason in state["log"][-1]["reason"]


def test_stop_blocks_new_orders_but_allows_cancel_reconciliation():
    events = buy() + [cmd("stop", "STOP"), cmd("cancel", "CANCEL"), cmd("sync", "RECONCILE"), cmd("again", "BUY", quantity="1")]
    state = replay(CONFIG, events)
    assert state["stopped"]
    assert state["reservedCash"] == "0.00000000"
    assert len(state["orders"]) == 1


@pytest.mark.parametrize("value", ["NaN", "Infinity", "-1", "0", "0.00001", "1000000001", "1e999999", 1.5])
def test_invalid_order_amounts(value):
    with pytest.raises(ValueError):
        replay(CONFIG, [cmd("bad", "BUY", quantity=value)])


def test_rejection_releases_reservation_and_pure_replay_preserves_inputs():
    events = buy() + [cmd("reject", "REJECT"), cmd("sync", "RECONCILE")]
    original = copy.deepcopy(events)
    result = replay(CONFIG, events)
    assert result["cash"] == "10000.00000000"
    assert result["reservedCash"] == "0.00000000"
    assert result == replay(CONFIG, events)
    assert events == original


def test_partial_sell_cost_rounding_and_ledger_equation():
    events = buy() + [cmd("fill", "FILL"), cmd("sync", "RECONCILE"),
                     cmd("sell", "SELL", quantity="3"), cmd("sd", "DISPATCH", "sell"),
                     cmd("sf", "PARTIAL_FILL", "sell", "1.0001"), cmd("sc", "CANCEL", "sell"), cmd("sr", "REVERSE", "sell")]
    state = replay(CONFIG, events)
    assert Decimal(state["equity"]) == Decimal(CONFIG["initialCapital"]) + Decimal(state["realizedPnl"]) + Decimal(state["unrealizedPnl"])
    assert state["reservedShares"] == "0.00000000"


def test_cash_and_weight_limits():
    config = {**CONFIG, "maxOrderValue": "10000", "maxPositionWeight": "1"}
    state = replay(config, [cmd("full", "BUY", quantity="100")])
    assert "현금 부족" in state["log"][-1]["reason"]
    state = replay({**config, "maxPositionWeight": "0.1"}, [cmd("large", "BUY", quantity="11")])
    assert "비중 초과" in state["log"][-1]["reason"]


def test_timeout_before_dispatch_requires_explicit_same_id_dispatch():
    events = [cmd("buy", "BUY", quantity="1"), cmd("timeout", "TIMEOUT"), cmd("r", "RECONCILE")]
    assert not replay(CONFIG, events)["broker"]
    state = replay(CONFIG, events + [cmd("d", "DISPATCH"), cmd("r2", "RECONCILE")])
    assert state["orders"]["buy"]["status"] == "ACCEPTED"


def test_strategy_sizes_on_server_and_respects_post_fee_weight():
    config = {**CONFIG, "maxOrderValue": "10000"}
    events = [cmd("buy", "STRATEGY_BUY"), cmd("d", "DISPATCH"), cmd("f", "FILL"), cmd("r", "RECONCILE")]
    state = replay(config, events)
    assert Decimal(state["shares"]) > 79
    assert Decimal(state["shares"]) * 100 / Decimal(state["equity"]) <= Decimal("0.8")
    state = replay(config, events + [cmd("again", "STRATEGY_BUY")])
    assert "again" not in state["orders"]


def test_pre_fee_weight_boundary_is_rejected():
    state = replay({**CONFIG, "maxOrderValue": "10000"}, [cmd("buy", "BUY", quantity="80")])
    assert "비중 초과" in state["log"][-1]["reason"]
