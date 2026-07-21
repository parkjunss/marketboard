"""Market-wide sentiment signals: CNN's Fear & Greed Index and an options put/call volume ratio.

Neither belongs in market_indices.py's OHLC daily-bar model -- these are point-in-time sentiment
snapshots, not a price series to chart.
"""

import fear_greed
import yfinance as yf

PUT_CALL_TICKER = "SPY"
# A single near-dated (especially 0DTE) expiration isn't representative of broader options
# positioning -- aggregate across the next few expirations instead. ~8 covers roughly the next
# couple of weeks of SPY's daily+weekly expirations and stays fast (~2s observed).
PUT_CALL_EXPIRATIONS_TO_AGGREGATE = 8


class FearGreedUnavailableError(Exception):
    pass


class PutCallDataUnavailableError(Exception):
    pass


def get_fear_greed() -> dict:
    """CNN's Fear & Greed Index via the `fear-greed` package, which hits CNN's internal data
    endpoint directly (not scraping HTML) -- but it's an undocumented, unofficial endpoint CNN
    could change or withdraw without notice, so callers should treat failures as expected and
    degrade gracefully rather than treat this as a hard dependency."""
    try:
        data = fear_greed.get()
    except Exception as exc:
        raise FearGreedUnavailableError(str(exc)) from exc
    timestamp = data["timestamp"]
    return {
        "score": data["score"],
        "rating": data["rating"],
        "timestamp": timestamp.isoformat() if hasattr(timestamp, "isoformat") else str(timestamp),
        "history": data["history"],
        "indicators": data["indicators"],
    }


def get_put_call_ratio(ticker: str = PUT_CALL_TICKER) -> dict:
    """Volume-based put/call ratio -- NOT open-interest-based. yfinance's `openInterest` column
    was observed returning ~0 across the board (a known yfinance data-quality gap) while `volume`
    is populated normally; open interest would silently produce a meaningless ratio."""
    t = yf.Ticker(ticker)
    expirations = t.options[:PUT_CALL_EXPIRATIONS_TO_AGGREGATE]
    if not expirations:
        raise PutCallDataUnavailableError(f"No option expirations available for {ticker}")

    total_call_volume = 0.0
    total_put_volume = 0.0
    for expiration in expirations:
        chain = t.option_chain(expiration)
        total_call_volume += float(chain.calls["volume"].fillna(0).sum())
        total_put_volume += float(chain.puts["volume"].fillna(0).sum())

    if total_call_volume == 0:
        raise PutCallDataUnavailableError(f"No options volume available for {ticker}")

    return {
        "ticker": ticker,
        "expirationsUsed": len(expirations),
        "callVolume": total_call_volume,
        "putVolume": total_put_volume,
        "putCallRatio": round(total_put_volume / total_call_volume, 3),
    }
