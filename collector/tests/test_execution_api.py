import pytest
from fastapi import HTTPException
from pydantic import ValidationError
from app.execution_api import ExecutionRequest, execution_replay
from tests.test_execution import CONFIG, cmd


def test_router_returns_authoritative_state():
    result = execution_replay(ExecutionRequest(configuration=CONFIG, commands=[cmd("buy", "BUY", quantity="10")]))
    assert result["valid"]
    assert result["state"]["reservedCash"] == "1000.50000000"


def test_bad_command_becomes_422():
    with pytest.raises(HTTPException) as error:
        execution_replay(ExecutionRequest(configuration=CONFIG, commands=[cmd("bad", "FILL")]))
    assert error.value.status_code == 422


def test_request_size_is_bounded():
    with pytest.raises(ValidationError):
        ExecutionRequest(configuration=CONFIG, commands=[cmd("buy", "BUY", quantity="1")] * 501)


def test_standalone_simulator_has_health_and_replay_routes():
    from app.execution_main import app, health

    assert health() == {"status": "ok", "mode": "simulation"}
    assert set(app.openapi()["paths"]) == {"/health", "/execution/replay", "/execution/auto-replay"}
    assert app.router.on_startup == []


def test_auto_comparison_is_opt_in_and_validates_input():
    from app.execution_api import AutoRequest, auto_replay
    from tests.test_auto_trading import CONFIG, BARS
    ordinary = auto_replay(AutoRequest(configuration=CONFIG, bars=BARS))
    comparison = auto_replay(AutoRequest(configuration=CONFIG, bars=BARS, comparison=True))
    assert "entries" in ordinary["state"]
    assert comparison["state"]["strategy"]["equity"] == ordinary["state"]["state"]["equity"]
    assert len(comparison["state"]["curve"]) == len(BARS)
    with pytest.raises(HTTPException) as error:
        auto_replay(AutoRequest(configuration=CONFIG, bars=BARS[::-1], comparison=True))
    assert error.value.status_code == 422
