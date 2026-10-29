"""Volatility risk state for SPY and the researched ETF universes (docs/Risk_Signal_Results.md).

20-day realized volatility anticipated the next 21 sessions' volatility and drawdown on 15 ETFs
across 2000-2026 (development, validation and a pre-registered holdout). It sizes risk, not
direction, so the state is a reference for judgment and never an order signal. The percentile
cut-offs below are display choices, not research results.
"""

from datetime import datetime, timezone
from math import sqrt

import numpy as np
import pandas as pd
import yfinance as yf

from .market_calendar import completed_sessions

ASSETS = (
    ("SPY", "S&P 500", "MULTI_ASSET"), ("QQQ", "NASDAQ 100", "MULTI_ASSET"), ("EFA", "선진국(미국 외)", "MULTI_ASSET"),
    ("EEM", "신흥국", "MULTI_ASSET"), ("IEF", "미국채 7-10년", "MULTI_ASSET"), ("GLD", "금", "MULTI_ASSET"),
    ("XLK", "기술", "SECTOR"), ("XLF", "금융", "SECTOR"), ("XLE", "에너지", "SECTOR"), ("XLV", "헬스케어", "SECTOR"),
    ("XLI", "산업재", "SECTOR"), ("XLY", "임의소비재", "SECTOR"), ("XLP", "필수소비재", "SECTOR"),
    ("XLU", "유틸리티", "SECTOR"), ("XLB", "소재", "SECTOR"),
)
VOL_WINDOW = 20
MIN_HISTORY = 252
CAUTION_PERCENTILE = 70
RISK_PERCENTILE = 90
EVIDENCE = {
    "score": "20일 실현 변동성",
    "target": "다음 21거래일 실현 변동성",
    "medianSpearman": {"development": 0.698, "validation": 0.429, "holdout": 0.507},
    "positiveAssets": {"development": 15, "validation": 15, "holdout": 15},
    "assets": 15,
    "period": "2000-2026",
    "drawdownMedianSpearman": {"development": 0.441, "validation": 0.224, "holdout": 0.260},
    "exposureRulesPassed": False,
}


def _state(percentile: float) -> str:
    if percentile >= RISK_PERCENTILE:
        return "RISK"
    if percentile >= CAUTION_PERCENTILE:
        return "CAUTION"
    return "NORMAL"


def assess(close: pd.Series) -> dict | None:
    """Current 20-day volatility and its percentile within the asset's own history up to today."""
    close = close.dropna()
    volatility = close.pct_change(fill_method=None).rolling(VOL_WINDOW).std(ddof=1).dropna() * sqrt(252)
    if len(volatility) < MIN_HISTORY:
        return None
    current = float(volatility.iloc[-1])
    percentile = float((volatility <= current).mean() * 100)
    peak = close.iloc[-252:].max()
    sma200 = close.iloc[-200:].mean()
    return {
        "volatility20Pct": round(current * 100, 2),
        "percentile": round(percentile, 1),
        "state": _state(percentile),
        "medianVolatilityPct": round(float(volatility.median()) * 100, 2),
        "drawdown252Pct": round(float(1 - close.iloc[-1] / peak) * 100, 2),
        "trend200Pct": round(float(close.iloc[-1] / sma200 - 1) * 100, 2),
        "historyStart": close.index[0].date().isoformat(),
        "tradingDate": close.index[-1].date().isoformat(),
    }


def _download() -> pd.DataFrame:
    frame = yf.download([t for t, _, _ in ASSETS], period="max", interval="1d", auto_adjust=True,
                        group_by="ticker", threads=True, progress=False)
    closes = pd.DataFrame({ticker: frame[ticker]["Close"] for ticker, _, _ in ASSETS
                           if ticker in frame.columns.get_level_values(0)})
    closes.index = pd.DatetimeIndex(closes.index).tz_localize(None)
    return closes


def get_risk_state(closes: pd.DataFrame | None = None, session: dict | None = None) -> dict:
    session = session or completed_sessions()
    closes = _download() if closes is None else closes
    # An in-progress session's bar is not a completed close; drop anything after the last completed session.
    closes = closes[closes.index <= pd.Timestamp(session["tradingDate"])]
    rows = []
    for ticker, name, group in ASSETS:
        result = assess(closes[ticker]) if ticker in closes else None
        if result is None:
            rows.append({"ticker": ticker, "name": name, "group": group, "state": "UNAVAILABLE"})
            continue
        status = "READY" if result["tradingDate"] == session["tradingDate"] else "STALE"
        rows.append({"ticker": ticker, "name": name, "group": group, "status": status, **result})
    counts = {state: sum(row["state"] == state for row in rows) for state in ("NORMAL", "CAUTION", "RISK", "UNAVAILABLE")}
    return {
        "expectedTradingDate": session["tradingDate"],
        "fetchedAt": datetime.now(timezone.utc).isoformat(),
        "source": "Yahoo Finance via yfinance",
        "adjustment": "YFINANCE_AUTO_ADJUST",
        "thresholds": {"caution": CAUTION_PERCENTILE, "risk": RISK_PERCENTILE, "validated": False},
        "evidence": EVIDENCE,
        "counts": counts,
        "assets": rows,
    }
