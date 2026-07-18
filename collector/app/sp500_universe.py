"""S&P 500 universe: batch-only membership + daily-bar price history, independent of the
real-time Finnhub WS symbol set. yfinance has no index-constituents endpoint, so the member
list comes from Wikipedia; new symbols are seeded inactive (see `mysql_writer.ensure_sp500_symbols`)
so this never silently expands the real-time WS subscription list.
"""

import logging
from datetime import timezone
from zoneinfo import ZoneInfo

import requests
import yfinance as yf
from bs4 import BeautifulSoup

from . import mysql_writer

logger = logging.getLogger("collector.sp500")

WIKI_URL = "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies"
NY_TZ = ZoneInfo("America/New_York")


def get_sp500_constituents() -> list[dict]:
    """Scrapes the current S&P 500 member list (ticker/name/sector) from Wikipedia."""
    resp = requests.get(WIKI_URL, headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
    resp.raise_for_status()
    soup = BeautifulSoup(resp.text, "html.parser")
    table = soup.find("table", {"id": "constituents"})
    rows = table.find("tbody").find_all("tr")[1:]

    constituents = []
    for row in rows:
        cells = row.find_all("td")
        if len(cells) < 3:
            continue
        ticker = cells[0].get_text(strip=True).replace(".", "-")  # yfinance wants BRK-B, not BRK.B
        name = cells[1].get_text(strip=True)
        sector = cells[2].get_text(strip=True)
        constituents.append({"ticker": ticker, "name": name, "sector": sector})
    return constituents


def _rows_from_history(symbol_id: int, history) -> list[tuple]:
    rows = []
    for idx, row in history.iterrows():
        if row[["Open", "High", "Low", "Close", "Volume"]].isna().any():
            continue
        # yf.download's multi-ticker index comes back naive; localize to the exchange's own
        # timezone (matches Ticker.history()'s tz-aware index) so daily bars land on the same
        # UTC timestamp as the existing single-ticker backfill path and merge via upsert
        # instead of creating duplicate rows for tickers already backfilled that way.
        ts = idx.tz_localize(NY_TZ) if idx.tzinfo is None else idx
        rows.append((
            symbol_id,
            ts.astimezone(timezone.utc).to_pydatetime(),
            float(row["Open"]),
            float(row["High"]),
            float(row["Low"]),
            float(row["Close"]),
            float(row["Volume"]),
            "1d",
        ))
    return rows


def run_sp500_batch(limit: int | None = None) -> dict:
    """Refreshes S&P 500 membership and daily bars (enough history for SMA50/RSI14). Returns a
    summary dict for logging/manual-trigger API use."""
    constituents = get_sp500_constituents()
    if limit:
        constituents = constituents[:limit]
    tickers = [c["ticker"] for c in constituents]

    symbol_ids = mysql_writer.ensure_sp500_symbols(constituents)

    # A single batched yf.download() call is dramatically cheaper than one Ticker.history() call
    # per ticker -- yfinance fans it out over a thread pool internally.
    data = yf.download(tickers, period="6mo", interval="1d", group_by="ticker", threads=True, progress=False)

    total_rows = 0
    failed_tickers = []
    for ticker in tickers:
        symbol_id = symbol_ids.get(ticker)
        if symbol_id is None:
            continue
        try:
            history = data[ticker] if len(tickers) > 1 else data
        except KeyError:
            failed_tickers.append(ticker)
            continue
        rows = _rows_from_history(symbol_id, history)
        if not rows:
            failed_tickers.append(ticker)
            continue
        total_rows += mysql_writer.insert_candles_bulk(rows)

    logger.info(
        "S&P 500 batch: %d constituents, %d candle rows written, %d ticker(s) with no data",
        len(tickers),
        total_rows,
        len(failed_tickers),
    )
    return {"constituents": len(tickers), "candle_rows": total_rows, "failed_tickers": failed_tickers}
