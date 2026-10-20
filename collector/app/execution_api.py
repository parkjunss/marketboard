from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from .execution import replay
from .auto_trading import replay_auto, compare_auto

router = APIRouter()


class AutoRequest(BaseModel):
    configuration: dict
    bars: list[dict] = Field(max_length=1000)
    comparison: bool = False
    warmup: list[dict] = Field(default_factory=list, max_length=200)


@router.post("/execution/auto-replay")
def auto_replay(payload: AutoRequest):
    try:
        calculate = compare_auto if payload.comparison else replay_auto
        return {"valid": True, "state": calculate(payload.configuration, payload.bars, payload.warmup)}
    except (ValueError, KeyError, TypeError, ArithmeticError) as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


class ExecutionRequest(BaseModel):
    configuration: dict
    commands: list[dict] = Field(max_length=500)


@router.post("/execution/replay")
def execution_replay(payload: ExecutionRequest):
    try:
        return {"valid": True, "state": replay(payload.configuration, payload.commands)}
    except (ValueError, KeyError, TypeError) as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
