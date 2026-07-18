"""Company basic info (name/exchange/sector/industry/currency/market cap) via yfinance `.info`.

Used to resolve arbitrary tickers a user types into a portfolio position: unlike the
active/watchlist symbol set, these aren't pre-seeded in `symbols`, so the backend needs a
real-time lookup the first time a new ticker shows up.
"""

import yfinance as yf


class UnknownSymbolError(Exception):
    pass


def get_symbol_profile(ticker: str) -> dict:
    info = yf.Ticker(ticker).info
    name = info.get("longName") or info.get("shortName")
    if not name:
        # yfinance doesn't raise for a bad ticker -- `.info` just comes back near-empty.
        raise UnknownSymbolError(f"Unknown ticker: {ticker}")
    return {
        "ticker": ticker,
        "name": name,
        "exchange": info.get("exchange"),
        "sector": info.get("sector"),
        "industry": info.get("industry"),
        "currency": info.get("currency"),
        "marketCap": info.get("marketCap"),
    }
