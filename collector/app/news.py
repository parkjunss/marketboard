"""Thin wrapper around Finnhub's News REST API: general market news and per-symbol company news."""

from datetime import date, timedelta

import requests

from . import config

_FINNHUB_REST_BASE = "https://finnhub.io/api/v1"


def get_general_news() -> list[dict]:
    resp = requests.get(
        f"{_FINNHUB_REST_BASE}/news",
        params={"category": "general", "token": config.FINNHUB_API_KEY},
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()


def get_company_news(symbol: str, days: int = 14) -> list[dict]:
    today = date.today()
    from_date = today - timedelta(days=days)
    resp = requests.get(
        f"{_FINNHUB_REST_BASE}/company-news",
        params={
            "symbol": symbol.upper(),
            "from": from_date.isoformat(),
            "to": today.isoformat(),
            "token": config.FINNHUB_API_KEY,
        },
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()
