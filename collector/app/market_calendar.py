"""US equity session boundaries; calendar completion does not prove data freshness."""
from datetime import date, datetime, timedelta, timezone
from functools import lru_cache

import exchange_calendars as xcals
import pandas as pd


@lru_cache(maxsize=4)
def _calendar(year: int):
    return xcals.get_calendar("XNYS", start=f"{year - 2}-01-01", end=f"{year + 1}-12-31")


def is_us_equity_session(day: str) -> bool:
    return bool(_calendar(date.fromisoformat(day).year).is_session(day))


def completed_sessions(now: datetime | None = None) -> dict:
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        raise ValueError("now must have a timezone")
    now = now.astimezone(timezone.utc)
    calendar = _calendar(now.year)
    sessions = calendar.sessions_in_range((now - timedelta(days=550)).date(), now.date())
    completed = [s for s in sessions if calendar.session_close(s) <= pd.Timestamp(now)]
    latest = completed[-1]
    return {
        "calendar": "XNYS", "timezone": "America/New_York",
        "calendarVersion": xcals.__version__,
        "evaluatedAt": now.isoformat(),
        "tradingDate": latest.date().isoformat(),
        "closedAt": calendar.session_close(latest).isoformat(),
        "sessionDates": [s.date().isoformat() for s in completed],
    }
