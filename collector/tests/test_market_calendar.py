from datetime import datetime

import pandas as pd
import pytest

from app.market_calendar import completed_sessions
from app import market_indices


@pytest.mark.parametrize("now,day,close", [
    ("2026-03-06T21:00:00+00:00", "2026-03-06", "21:00:00"),
    ("2026-03-09T19:59:59+00:00", "2026-03-06", "21:00:00"),
    ("2026-03-09T20:00:00+00:00", "2026-03-09", "20:00:00"),
    ("2026-11-02T21:00:00+00:00", "2026-11-02", "21:00:00"),
    ("2026-04-03T22:00:00+00:00", "2026-04-02", "20:00:00"),
    ("2026-07-03T22:00:00+00:00", "2026-07-02", "20:00:00"),
    ("2026-11-27T17:59:59+00:00", "2026-11-25", "21:00:00"),
    ("2026-11-27T18:00:00+00:00", "2026-11-27", "18:00:00"),
    ("2026-11-29T12:00:00+00:00", "2026-11-27", "18:00:00"),
    ("2026-01-01T23:00:00+00:00", "2025-12-31", "21:00:00"),
])
def test_completed_session_boundaries(now, day, close):
    result = completed_sessions(datetime.fromisoformat(now))
    assert result["tradingDate"] == day
    assert close in result["closedAt"]
    assert result["sessionDates"][-1] == day
    assert result["calendarVersion"]


def test_rejects_naive_clock():
    with pytest.raises(ValueError):
        completed_sessions(datetime(2026, 10, 23))


def test_missing_previous_session_is_not_a_one_day_return():
    history = [{"tradingDate": "2026-03-05", "close": 100}, {"tradingDate": "2026-03-09", "close": 110}]
    assert market_indices._pct_change(history, ["2026-03-05", "2026-03-06", "2026-03-09"], 1) is None


def test_equity_filters_open_session_but_macro_remains_unverified(monkeypatch):
    frame = pd.DataFrame({"Open": [100, 110], "High": [101, 111], "Low": [99, 109],
                          "Close": [100, 110], "Volume": [10, 20]},
                         index=pd.DatetimeIndex(["2026-03-06", "2026-03-09"], tz="America/New_York"))
    class Ticker:
        def __init__(self, symbol):
            pass
        def history(self, **kwargs):
            assert kwargs["auto_adjust"] is True
            return frame
    monkeypatch.setattr(market_indices.yf, "Ticker", Ticker)
    monkeypatch.setattr(market_indices, "completed_sessions",
                        lambda: completed_sessions(datetime.fromisoformat("2026-03-09T18:00:00+00:00")))
    equity = market_indices.get_index_history("SPX")
    assert len(equity) == 1
    assert equity[0]["tradingDate"] == "2026-03-06"
    assert equity[0]["sessionStatus"] == "COMPLETED"
    assert equity[0]["sourceSymbol"] == "^GSPC"
    assert equity[0]["fetchedAt"]
    macro = market_indices.get_index_history("GOLD")
    assert len(macro) == 2
    assert macro[-1]["sessionStatus"] == "UNVERIFIED"
    assert macro[-1]["calendar"] is None


def test_sector_stale_and_missing_history(monkeypatch):
    session = completed_sessions(datetime.fromisoformat("2026-03-09T21:00:00+00:00"))
    monkeypatch.setattr(market_indices, "completed_sessions", lambda: session)
    monkeypatch.setattr(market_indices, "SECTOR_INDICES", [
        {"slug": "XLK", "name": "Technology"}, {"slug": "XLF", "name": "Financials"}])
    monkeypatch.setattr(market_indices, "get_index_history", lambda slug, **kwargs: [] if slug == "XLF" else [
        {"close": 100, "tradingDate": "2026-03-05", "fetchedAt": "2026-03-09T21:00:00Z"},
        {"close": 105, "tradingDate": "2026-03-06", "fetchedAt": "2026-03-09T21:00:00Z"}])
    rows = market_indices.get_sector_performance()
    assert len(rows) == 1
    assert rows[0]["status"] == "STALE"
    assert rows[0]["tradingDate"] == "2026-03-06"
    assert rows[0]["expectedTradingDate"] == "2026-03-09"
    assert rows[0]["changePct1d"] == 5
    assert rows[0]["changePct1w"] is None
