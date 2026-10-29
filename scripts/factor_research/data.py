"""Download an immutable S&P 500 membership + daily-bar research snapshot without touching the application DB."""
import argparse
from datetime import datetime, timezone
import hashlib
from importlib.metadata import version
import json
from pathlib import Path
import shutil
import time

import exchange_calendars as xcals
import numpy as np
import pandas as pd
import yfinance as yf

MEMBERSHIP_FILE = "S&P 500 Historical Components & Changes (Updated).csv"
PRICE_START = "2000-01-01"
EVALUATION_START = "2001-01-01"
CHUNK_SIZE = 40
CHUNK_RETRIES = 3
MIN_COVERAGE = 0.90
HISTORY_DAYS = 252  # 12-1 momentum needs a year of bars before the ranking date
EXTREME_RETURN = 0.5


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def yahoo_symbol(ticker):
    return ticker.replace(".", "-")


def load_membership(path):
    frame = pd.read_csv(path)
    frame["date"] = pd.to_datetime(frame.date)
    if frame.date.duplicated().any() or not frame.date.is_monotonic_increasing:
        raise ValueError("Membership dates must be unique and ordered")
    frame["members"] = frame.tickers.str.split(",").map(frozenset)
    return frame[["date", "members"]]


def members_on(membership, date):
    """Latest membership row on or before `date`; never a later row."""
    row = membership.date.searchsorted(pd.Timestamp(date), side="right") - 1
    if row < 0:
        raise ValueError(f"No membership known on {date}")
    return membership.members.iloc[row]


def tickers_since(membership, start):
    first = max(membership.date.searchsorted(pd.Timestamp(start), side="right") - 1, 0)
    return sorted(set().union(*membership.members.iloc[first:]))


def completed_sessions(start, as_of):
    cal = xcals.get_calendar("XNYS", start=start, end=f"{as_of.year + 1}-12-31")
    sessions = cal.sessions[cal.sessions <= pd.Timestamp(as_of.date())]
    return [s for s in sessions if cal.session_close(s) <= as_of]


def download_chunk(tickers, start, end):
    symbols = [yahoo_symbol(t) for t in tickers]
    for attempt in range(CHUNK_RETRIES):
        try:
            frame = yf.download(symbols, start=start, end=end, interval="1d", auto_adjust=False, actions=True,
                                group_by="ticker", threads=True, progress=False, repair=False, keepna=False)
            break
        except Exception:  # yfinance raises assorted network errors; a final failure leaves the chunk missing
            if attempt == CHUNK_RETRIES - 1:
                raise
            time.sleep(5 * (attempt + 1))
    rows = []
    for ticker, symbol in zip(tickers, symbols):
        if symbol not in frame.columns.get_level_values(0):
            continue
        data = frame[symbol].dropna(subset=["Close"])
        if data.empty:
            continue
        index = data.index.tz_convert("America/New_York") if data.index.tz is not None else data.index
        factor = data["Adj Close"].to_numpy() / data.Close.to_numpy()
        part = pd.DataFrame({"date": index.strftime("%Y-%m-%d"), "ticker": ticker})
        for field in ("Open", "High", "Low", "Close", "Volume"):
            part[field.lower()] = data[field].to_numpy()
        part["adj_close"] = data["Adj Close"].to_numpy()
        part["adj_open"] = part.open * factor
        part["dividends"] = data["Dividends"].to_numpy() if "Dividends" in data else 0.0
        part["stock_splits"] = data["Stock Splits"].to_numpy() if "Stock Splits" in data else 0.0
        rows.append(part)
    return rows


def audit(prices, sessions):
    errors, review = [], {}
    if prices.duplicated(["ticker", "date"]).any():
        errors.append("duplicate ticker sessions")
    if not prices.date.isin(sessions).all():
        errors.append("bars outside completed XNYS sessions")
    ohlc = prices[["open", "high", "low", "close", "adj_close", "adj_open"]]
    finite = np.isfinite(ohlc.to_numpy()).all(axis=1) & (ohlc > 0).all(axis=1).to_numpy()
    bounded = ((prices.low <= prices[["open", "close"]].min(axis=1) + 1e-9)
               & (prices.high >= prices[["open", "close"]].max(axis=1) - 1e-9)).to_numpy()
    review["invalidBars"] = int((~(finite & bounded)).sum())
    returns = prices.groupby("ticker").adj_close.pct_change(fill_method=None)
    extreme = prices.loc[returns.abs() > EXTREME_RETURN, ["ticker", "date"]]
    review["extremeDailyReturns"] = int(len(extreme))
    return errors, review, extreme.assign(adjReturn=returns[extreme.index])


def coverage(membership, prices, sessions):
    """Month-end share of then-current members that have a bar that day and a full momentum history."""
    by_ticker = {ticker: dates for ticker, dates in prices.groupby("ticker").date}
    position = {ticker: {date: i for i, date in enumerate(dates)} for ticker, dates in by_ticker.items()}
    frame = pd.Series(pd.to_datetime(sessions))
    month_ends = frame.groupby(frame.dt.to_period("M")).max()
    rows = []
    for date in month_ends[month_ends >= pd.Timestamp(EVALUATION_START)]:
        key = date.strftime("%Y-%m-%d")
        members = members_on(membership, date)
        priced = [t for t in members if key in position.get(t, {})]
        history = [t for t in priced if position[t][key] >= HISTORY_DAYS]
        rows.append(dict(date=key, members=len(members), priced=len(priced), withHistory=len(history),
                         pricedShare=len(priced) / len(members), historyShare=len(history) / len(members)))
    return pd.DataFrame(rows)


def download(source, output, as_of, limit=None):
    source, output = Path(source), Path(output)
    if output.exists():
        raise ValueError("Choose a new snapshot directory; existing snapshots are immutable")
    cutoff = pd.Timestamp(as_of)
    if cutoff.tzinfo is None:
        raise ValueError("as-of must include timezone")
    membership = load_membership(source / MEMBERSHIP_FILE)
    tickers = tickers_since(membership, EVALUATION_START)[:limit]
    complete = completed_sessions(PRICE_START, cutoff)
    sessions = [s.date().isoformat() for s in complete]
    end = (complete[-1] + pd.Timedelta(days=1)).date().isoformat()
    parts = []
    for offset in range(0, len(tickers), CHUNK_SIZE):
        parts += download_chunk(tickers[offset:offset + CHUNK_SIZE], PRICE_START, end)
        print(f"Downloaded {min(offset + CHUNK_SIZE, len(tickers))}/{len(tickers)}", flush=True)
        time.sleep(2)
    prices = pd.concat(parts, ignore_index=True).sort_values(["ticker", "date"]).reset_index(drop=True)
    errors, review, extreme = audit(prices, sessions)
    output.mkdir(parents=True)
    membership_dir = output / "membership"
    membership_dir.mkdir()
    for name in (MEMBERSHIP_FILE, "LICENSE", "sp500_ticker_start_end.csv"):
        shutil.copy2(source / name, membership_dir / name)
    prices.to_csv(output / "prices.csv.gz", index=False, float_format="%.10g")
    extreme.to_csv(output / "extreme-returns.csv", index=False)
    serialized = pd.read_csv(output / "prices.csv.gz")
    assert len(serialized) == len(prices)
    table = coverage(membership, serialized, sessions)
    table.to_csv(output / "coverage.csv", index=False)
    priced = set(serialized.ticker)
    missing = [t for t in tickers if t not in priced]
    pd.DataFrame({"ticker": missing}).to_csv(output / "missing-tickers.csv", index=False)
    evaluation = table[table.date < membership.date.iloc[-1].strftime("%Y-%m-%d")]
    failing = evaluation[evaluation.historyShare < MIN_COVERAGE]
    passing = evaluation[evaluation.historyShare >= MIN_COVERAGE]
    manifest = dict(
        source="yfinance/Yahoo Finance daily bars + fja05680/sp500 membership (MIT)",
        asOf=cutoff.isoformat(), downloadedAt=datetime.now(timezone.utc).isoformat(),
        priceStart=PRICE_START, endExclusive=end, evaluationStart=EVALUATION_START,
        membershipLastDate=membership.date.iloc[-1].strftime("%Y-%m-%d"),
        versions={p: version(p) for p in ("yfinance", "pandas", "exchange-calendars")},
        autoAdjust=False, adjustment="adj_open = open * Adj Close / Close; adj_close from Yahoo",
        hashes={"prices.csv.gz": sha256(output / "prices.csv.gz"),
                **{f"membership/{n}": sha256(membership_dir / n) for n in (MEMBERSHIP_FILE, "sp500_ticker_start_end.csv")}},
        tickersRequested=len(tickers), tickersPriced=len(priced), tickersMissing=len(missing),
        bars=len(serialized), errors=errors, review=review,
        coverage=dict(minimum=MIN_COVERAGE, historyDays=HISTORY_DAYS, months=len(evaluation),
                      monthsBelowMinimum=len(failing),
                      firstMonthAtOrAboveMinimum=passing.date.min() if len(passing) else None,
                      meanHistoryShare=float(evaluation.historyShare.mean()) if len(evaluation) else None),
        survivorship="Yahoo keeps few delisted symbols; missing members are excluded, which biases results upward",
        tickerReuse="Reused symbols may map to a later company; review extreme-returns.csv and membership intervals",
        pointInTimeVintage=False, researchOnly=True, limited=limit is not None)
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps({k: manifest[k] for k in ("tickersRequested", "tickersPriced", "bars", "errors", "review", "coverage")},
                     indent=2), flush=True)
    if errors:
        raise ValueError("Snapshot rejected; see manifest errors")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, help="directory holding the fja05680/sp500 membership files")
    parser.add_argument("--output", required=True)
    parser.add_argument("--as-of", default=datetime.now(timezone.utc).isoformat())
    parser.add_argument("--limit", type=int, help="smoke run on the first N tickers")
    args = parser.parse_args()
    download(args.source, args.output, args.as_of, args.limit)
