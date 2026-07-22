"""Options-derived support/resistance levels, sourced from CBOE's public delayed-quotes
API rather than yfinance.

yfinance's option_chain() has no usable open interest or bid/ask: Yahoo's unofficial
options endpoint returns bid=0/ask=0/openInterest=0 for every single contract,
confirmed regardless of market hours (checked pre-market, during market, same result).
Cross-checking the exact same strikes against CBOE's own public delayed-quotes JSON
(the same feed cboe.com's own website renders from) shows real, populated values for
those fields, with volume matching Yahoo's numbers exactly -- so it's the same
underlying market, just Yahoo's free/unauthenticated endpoint strips OI and live
quotes. CBOE's endpoint also returns every expiration in a single request (~3,500
contracts for a name like AAPL), instead of one yfinance call per expiration.
"""

import re

import requests

CBOE_BASE_URL = "https://cdn.cboe.com/api/global/delayed_quotes/options"
# OCC option symbol format: {root}{YYMMDD}{C|P}{strike * 1000, zero-padded to 8 digits}
# e.g. AAPL260722C00205000 = AAPL, 2026-07-22 expiration, Call, strike 205.000
OCC_SYMBOL_PATTERN = re.compile(r"^[A-Z]+(\d{6})([CP])(\d{8})$")
TOP_LEVELS_COUNT = 5


class OptionsLevelsUnavailableError(Exception):
    pass


def get_options_levels(ticker: str) -> dict:
    """Max pain plus the top open-interest strikes (support/resistance candidates) for
    the nearest available expiration."""
    try:
        response = requests.get(f"{CBOE_BASE_URL}/{ticker}.json", timeout=10)
        response.raise_for_status()
    except requests.RequestException as exc:
        # CBOE 403s (not 404) for tickers it doesn't have a listing for -- an S3
        # static-hosting quirk (AccessDenied on a missing key), not an auth failure.
        raise OptionsLevelsUnavailableError(f"No options data available for {ticker}") from exc

    payload = response.json()
    data = payload.get("data", {})
    spot_price = data.get("current_price")

    contracts = []
    for opt in data.get("options", []):
        match = OCC_SYMBOL_PATTERN.match(opt.get("option", ""))
        if not match:
            continue
        exp_digits, call_put, strike_digits = match.groups()
        contracts.append(
            {
                "expiration": f"20{exp_digits[:2]}-{exp_digits[2:4]}-{exp_digits[4:6]}",
                "is_call": call_put == "C",
                "strike": int(strike_digits) / 1000,
                "open_interest": opt.get("open_interest") or 0,
            }
        )
    if not contracts:
        raise OptionsLevelsUnavailableError(f"No options data available for {ticker}")

    nearest_expiration = min(c["expiration"] for c in contracts)
    nearest = [c for c in contracts if c["expiration"] == nearest_expiration]

    calls_by_strike: dict[float, int] = {}
    puts_by_strike: dict[float, int] = {}
    for c in nearest:
        bucket = calls_by_strike if c["is_call"] else puts_by_strike
        bucket[c["strike"]] = bucket.get(c["strike"], 0) + int(c["open_interest"])

    resistance = sorted(calls_by_strike.items(), key=lambda kv: kv[1], reverse=True)[:TOP_LEVELS_COUNT]
    support = sorted(puts_by_strike.items(), key=lambda kv: kv[1], reverse=True)[:TOP_LEVELS_COUNT]

    return {
        "ticker": ticker,
        "expiration": nearest_expiration,
        "spotPrice": spot_price,
        "maxPain": _compute_max_pain(calls_by_strike, puts_by_strike),
        "resistanceLevels": [{"strike": strike, "openInterest": oi} for strike, oi in sorted(resistance)],
        "supportLevels": [{"strike": strike, "openInterest": oi} for strike, oi in sorted(support)],
    }


def _compute_max_pain(calls_by_strike: dict[float, int], puts_by_strike: dict[float, int]) -> float | None:
    """The strike at which option *writers* (sellers) owe the least total intrinsic
    value to holders at expiration -- a commonly-cited "pin" price the underlying
    tends to gravitate toward as expiration approaches, since writers are the
    market's dominant hedgers."""
    all_strikes = sorted(set(calls_by_strike) | set(puts_by_strike))
    if not all_strikes:
        return None

    best_strike, best_payout = None, None
    for candidate in all_strikes:
        payout = 0.0
        for strike, oi in calls_by_strike.items():
            if candidate > strike:
                payout += (candidate - strike) * oi
        for strike, oi in puts_by_strike.items():
            if candidate < strike:
                payout += (strike - candidate) * oi
        if best_payout is None or payout < best_payout:
            best_strike, best_payout = candidate, payout
    return best_strike
