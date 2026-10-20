"""Standalone simulation service: no market feeds or background network jobs."""

from fastapi import FastAPI
from app.execution_api import router

app = FastAPI(title="MarketBoard Execution Simulator")
app.include_router(router)


@app.get("/health")
def health():
    return {"status": "ok", "mode": "simulation"}
