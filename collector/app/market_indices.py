"""Daily OHLC history for broad market indices via yfinance.

Indices aren't on Finnhub's realtime feed, so unlike tracked stocks this is a
daily-only, fetch-on-request path (no WS ticks, no MySQL storage) — each call
just pulls fresh daily candles from yfinance.
"""

from datetime import timezone

import yfinance as yf

MACRO_INDICES = [
    {"slug": "SPX", "yf_ticker": "^GSPC", "name": "S&P 500"},
    {"slug": "IXIC", "yf_ticker": "^IXIC", "name": "NASDAQ"},
    {"slug": "RUT", "yf_ticker": "^RUT", "name": "Russell 2000"},
    {"slug": "TSX", "yf_ticker": "^GSPTSE", "name": "S&P/TSX"},
    {"slug": "VIX", "yf_ticker": "^VIX", "name": "Volatility Index"},
    {"slug": "DXY", "yf_ticker": "DX-Y.NYB", "name": "US Dollar Index"},
    {"slug": "US5Y", "yf_ticker": "^FVX", "name": "Treasury Yield 5 Years"},
    # 10-year is the most-watched maturity (mortgage rates, equity valuation discount rate,
    # recession-signal inversions vs 2Y) -- 5Y/30Y alone skip right past it.
    {"slug": "US10Y", "yf_ticker": "^TNX", "name": "Treasury Yield 10 Years"},
    {"slug": "US30Y", "yf_ticker": "^TYX", "name": "Treasury Yield 30 Years"},
    {"slug": "USDKRW", "yf_ticker": "KRW=X", "name": "USD/KRW"},
]

# The 11 GICS sector SPDR ETFs -- the standard retail proxy for sector rotation/relative strength
# (compare each sector's return over a window against the others, not against a dollar-flow
# figure we don't have access to). Kept separate from MACRO_INDICES so the main index grid isn't
# cluttered with 11 more cards -- see get_sector_performance() for the ranked view these feed.
SECTOR_INDICES = [
    {"slug": "XLK", "yf_ticker": "XLK", "name": "Technology"},
    {"slug": "XLF", "yf_ticker": "XLF", "name": "Financials"},
    {"slug": "XLE", "yf_ticker": "XLE", "name": "Energy"},
    {"slug": "XLV", "yf_ticker": "XLV", "name": "Health Care"},
    {"slug": "XLI", "yf_ticker": "XLI", "name": "Industrials"},
    {"slug": "XLY", "yf_ticker": "XLY", "name": "Consumer Discretionary"},
    {"slug": "XLP", "yf_ticker": "XLP", "name": "Consumer Staples"},
    {"slug": "XLU", "yf_ticker": "XLU", "name": "Utilities"},
    {"slug": "XLB", "yf_ticker": "XLB", "name": "Materials"},
    {"slug": "XLRE", "yf_ticker": "XLRE", "name": "Real Estate"},
    {"slug": "XLC", "yf_ticker": "XLC", "name": "Communication Services"},
]

INDICES = MACRO_INDICES + SECTOR_INDICES
_BY_SLUG = {i["slug"]: i for i in INDICES}


def list_indices() -> list[dict]:
    return [{"slug": i["slug"], "name": i["name"]} for i in MACRO_INDICES]


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


def _pct_change(closes: list[float], trading_days_back: int) -> float | None:
    idx = len(closes) - 1 - trading_days_back
    if idx < 0:
        return None
    base = closes[idx]
    return ((closes[-1] - base) / base) * 100 if base else None


def get_sector_performance() -> list[dict]:
    """1일/1주/1개월 % return for each SPDR sector ETF, sorted by 1개월 return descending -- the
    sector at the top is the one that's been "hot" over the past month relative to the others,
    the closest proxy for sector rotation retail tools actually use without paid fund-flow data."""
    results = []
    for sector in SECTOR_INDICES:
        history = get_index_history(sector["slug"], period="2mo")
        if len(history) < 2:
            continue
        closes = [h["close"] for h in history]
        results.append(
            {
                "slug": sector["slug"],
                "name": sector["name"],
                "changePct1d": _pct_change(closes, 1),
                "changePct1w": _pct_change(closes, 5),
                "changePct1m": _pct_change(closes, 21),
            }
        )
    results.sort(key=lambda r: r["changePct1m"] if r["changePct1m"] is not None else float("-inf"), reverse=True)
    return results
