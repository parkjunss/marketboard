import numpy as np
import pandas as pd

from app.risk_state import ASSETS, assess, get_risk_state


def _closes(days=600, shock_from=None, seed=0):
    generator = np.random.default_rng(seed)
    returns = generator.normal(0, 0.005, days)
    if shock_from is not None:
        returns[shock_from:] = generator.normal(0, 0.04, days - shock_from)
    index = pd.bdate_range("2020-01-01", periods=days)
    return pd.Series(100 * np.cumprod(1 + returns), index=index)


def test_recent_volatility_spike_is_flagged_as_risk():
    result = assess(_closes(shock_from=580))
    assert result["state"] == "RISK"
    assert result["percentile"] >= 90


def test_calm_series_stays_normal_and_short_history_is_unavailable():
    calm = _closes()
    calm.iloc[-30:] = calm.iloc[-31]  # flat recent prices -> lowest volatility
    assert assess(calm)["state"] == "NORMAL"
    assert assess(_closes(days=200)) is None


def test_incomplete_session_bar_is_dropped_and_staleness_reported():
    series = _closes()
    closes = pd.DataFrame({ticker: series for ticker, _, _ in ASSETS})
    last = series.index[-1].date().isoformat()
    previous = series.index[-2].date().isoformat()
    result = get_risk_state(closes, {"tradingDate": previous})
    assert all(row["tradingDate"] == previous and row["status"] == "READY" for row in result["assets"])
    stale = get_risk_state(closes.drop(columns="SPY").assign(SPY=series.iloc[:-5]), {"tradingDate": last})
    spy = next(row for row in stale["assets"] if row["ticker"] == "SPY")
    assert spy["status"] == "STALE"
    assert stale["thresholds"]["validated"] is False
