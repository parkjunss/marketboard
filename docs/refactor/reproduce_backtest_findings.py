"""Read-only diagnostic for the 2026-09-10 refactoring assessment.

Run from the repository root with collector/.venv/Scripts/python.exe -B
docs/refactor/reproduce_backtest_findings.py. No DB or network calls.
Assertions describe intended behavior after the fix; no code or data is edited.
"""
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "collector"))
import pandas as pd
from app.backtest import InsufficientDataError, _compute_backtest

prices = pd.DataFrame(
    {"AAA": [100.0, 110.0, 110.0], "SPY": [100.0, 100.0, 100.0]},
    index=[date(2026, 1, 30), date(2026, 2, 2), date(2026, 2, 3)],
)
result = _compute_backtest(
    prices, ["AAA"], 1000.0, 0.0, "PERIODIC_REBALANCE", {"rebalanceFrequency": "MONTHLY"}
)
actual = result["equityCurve"][-1]["portfolioValue"]
print(f"month_boundary: expected=1100.0 actual={actual} mismatch={actual != 1100.0}")
assert actual == 1100.0

try:
    _compute_backtest(prices, ["AAA", "MISSING"], 1000.0, 0.0)
except InsufficientDataError as exc:
    assert "MISSING" in str(exc)
    print(f"missing_ticker: correctly rejected: {exc}")
else:
    raise AssertionError("Missing requested ticker was silently excluded")
