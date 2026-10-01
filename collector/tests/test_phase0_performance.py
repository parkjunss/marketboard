"""Repeatable Phase 0 CPU baselines.

Run from collector/ with:
    uv run pytest tests/test_phase0_performance.py -m performance -s

Database and external API I/O are replaced with fixed data so later refactors can be compared
against the same workload. These are measurements, not timing assertions: shared CI machines are
too noisy for a stable pass/fail threshold.
"""

from __future__ import annotations

import json
import math
import platform
import statistics
import time
from datetime import date

import pandas as pd
import pytest

from app.backtest import BENCHMARK_TICKER, _compute_backtest
from app import screener


WARMUP_RUNS = 3
MEASURED_RUNS = 20


def _percentile(samples: list[float], percentile: float) -> float:
    ordered = sorted(samples)
    rank = (len(ordered) - 1) * percentile
    lower = math.floor(rank)
    upper = math.ceil(rank)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (rank - lower)


def _measure(name: str, operation) -> dict[str, object]:
    for _ in range(WARMUP_RUNS):
        operation()

    samples_ms = []
    for _ in range(MEASURED_RUNS):
        started = time.perf_counter_ns()
        operation()
        samples_ms.append((time.perf_counter_ns() - started) / 1_000_000)

    result = {
        "name": name,
        "runs": MEASURED_RUNS,
        "p50Ms": round(_percentile(samples_ms, 0.50), 3),
        "p95Ms": round(_percentile(samples_ms, 0.95), 3),
        "p99Ms": round(_percentile(samples_ms, 0.99), 3),
        "meanMs": round(statistics.fmean(samples_ms), 3),
        "minMs": round(min(samples_ms), 3),
        "maxMs": round(max(samples_ms), 3),
    }
    print(json.dumps(result, sort_keys=True))
    return result


def _screener_closes() -> pd.DataFrame:
    days = screener.DEFAULT_TREND_MA_WINDOW + screener.DEFAULT_MOMENTUM_WINDOW_DAYS + 5
    dates = pd.date_range(date(2024, 1, 1), periods=days, freq="B").date
    data = {}
    for ticker_number in range(503):
        drift = 0.0002 + ticker_number * 0.000001
        data[f"T{ticker_number:03d}"] = [
            100.0 * ((1 + drift) ** day) * (1 + 0.002 * math.sin(day / 7 + ticker_number))
            for day in range(days)
        ]
    return pd.DataFrame(data, index=dates)


def _backtest_closes() -> tuple[pd.DataFrame, list[str]]:
    dates = pd.date_range(date(2016, 1, 1), periods=2520, freq="B").date
    tickers = [f"T{i:02d}" for i in range(10)]
    data = {
        ticker: [
            100.0 * ((1.0002 + index * 0.00001) ** day) * (1 + 0.003 * math.sin(day / 11 + index))
            for day in range(len(dates))
        ]
        for index, ticker in enumerate(tickers)
    }
    data[BENCHMARK_TICKER] = [100.0 * (1.00025**day) for day in range(len(dates))]
    return pd.DataFrame(data, index=dates), tickers


@pytest.mark.performance
def test_phase0_screener_cpu_baseline(monkeypatch):
    closes = _screener_closes()
    monkeypatch.setattr(screener, "_load_universe_closes", lambda *args, **kwargs: closes)
    monkeypatch.setattr(
        screener,
        "_enrich",
        lambda ticker: {
            **screener._EMPTY_FUNDAMENTALS,
            "marketCap": 100_000_000_000,
            "newsSentiment": 0.0,
            "newsCount": 0,
        },
    )

    result = screener.run_screener(top_n=10)
    assert result["universeSize"] == 503
    assert result["screenedCount"] == 503

    measurement = _measure("collector.screener.cpu", lambda: screener.run_screener(top_n=10))
    assert measurement["runs"] == MEASURED_RUNS


@pytest.mark.performance
def test_phase0_backtest_cpu_baseline():
    closes, tickers = _backtest_closes()

    result = _compute_backtest(closes, tickers, 100_000.0, 3.5)
    assert len(result["equityCurve"]) == 2520
    assert len(result["tickerStats"]) == 10

    measurement = _measure(
        "collector.backtest.cpu",
        lambda: _compute_backtest(closes, tickers, 100_000.0, 3.5),
    )
    assert measurement["runs"] == MEASURED_RUNS


@pytest.fixture(scope="session", autouse=True)
def print_phase0_environment():
    print(
        json.dumps(
            {
                "machine": platform.machine(),
                "pandas": pd.__version__,
                "platform": platform.platform(),
                "python": platform.python_version(),
            },
            sort_keys=True,
        )
    )
