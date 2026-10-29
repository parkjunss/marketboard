"""Download an immutable daily-bar snapshot for the pre-registered ETF universes."""
import argparse
from datetime import datetime, timezone
from importlib.metadata import version
import json
from pathlib import Path

import pandas as pd

from .data import audit, completed_sessions, download_chunk, sha256

UNIVERSES = {
    "U1": ("XLB", "XLE", "XLF", "XLI", "XLK", "XLP", "XLU", "XLV", "XLY"),
    "U2": ("SPY", "QQQ", "EFA", "EEM", "IEF", "GLD"),
}
PRICE_START = "1998-12-01"


def missing_sessions(prices, sessions):
    """XNYS sessions absent between each ticker's first and last bar."""
    result = {}
    for ticker, dates in prices.groupby("ticker").date:
        span = [s for s in sessions if dates.iloc[0] <= s <= dates.iloc[-1]]
        result[ticker] = sorted(set(span) - set(dates))
    return result


def download(output, as_of):
    output = Path(output)
    if output.exists():
        raise ValueError("Choose a new snapshot directory; existing snapshots are immutable")
    cutoff = pd.Timestamp(as_of)
    if cutoff.tzinfo is None:
        raise ValueError("as-of must include timezone")
    tickers = sorted(set().union(*UNIVERSES.values()))
    complete = completed_sessions(PRICE_START, cutoff)
    sessions = [s.date().isoformat() for s in complete]
    end = (complete[-1] + pd.Timedelta(days=1)).date().isoformat()
    prices = pd.concat(download_chunk(tickers, PRICE_START, end), ignore_index=True)
    prices = prices.sort_values(["ticker", "date"]).reset_index(drop=True)
    errors, review, extreme = audit(prices, sessions)
    if set(prices.ticker) != set(tickers):
        errors.append(f"missing tickers: {sorted(set(tickers) - set(prices.ticker))}")
    gaps = missing_sessions(prices, sessions)
    if any(gaps.values()):
        errors.append("missing sessions inside ticker history")
    output.mkdir(parents=True)
    prices.to_csv(output / "prices.csv", index=False, float_format="%.10g")
    extreme.to_csv(output / "extreme-returns.csv", index=False)
    serialized = pd.read_csv(output / "prices.csv")
    assert len(serialized) == len(prices)
    first = serialized.groupby("ticker").date.min().to_dict()
    manifest = dict(source="yfinance/Yahoo Finance daily bars", universes=UNIVERSES, asOf=cutoff.isoformat(),
                    downloadedAt=datetime.now(timezone.utc).isoformat(), priceStart=PRICE_START, endExclusive=end,
                    versions={p: version(p) for p in ("yfinance", "pandas", "exchange-calendars")},
                    autoAdjust=False, adjustment="adj_open = open * Adj Close / Close; adj_close from Yahoo",
                    sha256=sha256(output / "prices.csv"), bars=len(serialized), firstBar=first,
                    missingSessions={t: g[:20] for t, g in gaps.items() if g}, errors=errors, review=review,
                    pointInTimeVintage=False, researchOnly=True)
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps({k: manifest[k] for k in ("bars", "firstBar", "missingSessions", "errors", "review")}, indent=2))
    if errors:
        raise ValueError("Snapshot rejected; see manifest errors")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--as-of", default=datetime.now(timezone.utc).isoformat())
    args = parser.parse_args()
    download(args.output, args.as_of)
