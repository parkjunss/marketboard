"""Daily OHLC history for broad market indices via yfinance.

Indices aren't on Finnhub's realtime feed, so unlike tracked stocks this is a
daily-only, fetch-on-request path (no WS ticks, no MySQL storage) — each call
just pulls fresh daily candles from yfinance.
"""

from datetime import timezone

import yfinance as yf

INDICES = [
    {"slug": "SPX", "yf_ticker": "^GSPC", "name": "S&P 500"},
    {"slug": "IXIC", "yf_ticker": "^IXIC", "name": "NASDAQ"},
    {"slug": "RUT", "yf_ticker": "^RUT", "name": "Russell 2000"},
    {"slug": "TSX", "yf_ticker": "^GSPTSE", "name": "S&P/TSX"},
    {"slug": "VIX", "yf_ticker": "^VIX", "name": "Volatility Index"},
    {"slug": "DXY", "yf_ticker": "DX-Y.NYB", "name": "US Dollar Index"},
    {"slug": "US5Y", "yf_ticker": "^FVX", "name": "Treasury Yield 5 Years"},
    {"slug": "US30Y", "yf_ticker": "^TYX", "name": "Treasury Yield 30 Years"},
]

_BY_SLUG = {i["slug"]: i for i in INDICES}


def list_indices() -> list[dict]:
    return [{"slug": i["slug"], "name": i["name"]} for i in INDICES]


def get_index_history(slug: str, period: str = "6mo") -> list[dict]:
    index = _BY_SLUG[slug]
    data = yf.Ticker(index["yf_ticker"]).history(period=period, interval="1d")
    rows = []
    for ts, row in data.iterrows():
        utc_ts = ts.to_pydatetime().astimezone(timezone.utc)
        volume = row["Volume"]
        rows.append(
            {
                "ts": utc_ts.strftime("%Y-%m-%dT%H:%M:%SZ"),
                "open": float(row["Open"]),
                "high": float(row["High"]),
                "low": float(row["Low"]),
                "close": float(row["Close"]),
                # Indices/yields report no volume (NaN) — guard rather than emit NaN into JSON.
                "volume": float(volume) if volume == volume else 0.0,
            }
        )
    return rows
