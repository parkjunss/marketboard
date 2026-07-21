"""S&P 500 universe: batch-only membership + daily-bar price history, independent of the
real-time Finnhub WS symbol set. yfinance has no index-constituents endpoint, so the member
list comes from Wikipedia; new symbols are seeded inactive (see `mysql_writer.ensure_sp500_symbols`)
so this never silently expands the real-time WS subscription list.
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor
from concurrent.futures import TimeoutError as FutureTimeoutError
from datetime import timezone
from zoneinfo import ZoneInfo

import requests
import yfinance as yf
from bs4 import BeautifulSoup

from . import mysql_writer

logger = logging.getLogger("collector.sp500")

WIKI_URL = "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies"
NY_TZ = ZoneInfo("America/New_York")

# A single yf.download() call for all ~500 S&P 500 tickers at once has proven unreliable --
# observed hanging 8+ minutes with zero response. The actual root cause turned out to be
# elsewhere though (see mysql_writer.insert_candles_bulk's docstring: opening a fresh MySQL
# connection per ticker was adding ~10s of latency *per call*, which is what made the whole batch
# appear to hang -- the yfinance download step itself was always fast, 2-4s per chunk). Chunking
# is still kept as defense in depth: a slow/failed chunk only costs CHUNK_SIZE tickers via
# CHUNK_TIMEOUT_SECONDS, not the whole run, and CHUNK_DELAY_SECONDS spaces out requests.
CHUNK_SIZE = 40
# Sized for the routine 6mo daily run (~2-4s/chunk per the comment above); a one-off deep
# backfill (period="5y") pulls far more data per chunk and was observed timing out at 60s for
# ~3 of 13 chunks, silently leaving those tickers on the old 6mo window. 120s gives that case
# headroom without slowing down the fast, common case (it's a ceiling, not a fixed wait).
CHUNK_TIMEOUT_SECONDS = 120
CHUNK_DELAY_SECONDS = 2


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


def _download_chunk(chunk_tickers: list[str], period: str):
    return yf.download(
        chunk_tickers, period=period, interval="1d", group_by="ticker", threads=True, progress=False, timeout=15
    )


def _download_chunk_with_timeout(chunk_tickers: list[str], period: str):
    """Runs yf.download() for one chunk with a hard wall-clock timeout. If it times out, the
    underlying thread is abandoned (Python can't forcibly kill a running thread) rather than
    blocked on -- shutdown(wait=False) lets this function return promptly so the caller can move
    on to the next chunk instead of the whole batch hanging on one bad chunk."""
    executor = ThreadPoolExecutor(max_workers=1)
    future = executor.submit(_download_chunk, chunk_tickers, period)
    try:
        return future.result(timeout=CHUNK_TIMEOUT_SECONDS)
    except FutureTimeoutError:
        logger.warning("S&P 500 batch: chunk of %d ticker(s) timed out after %ds", len(chunk_tickers), CHUNK_TIMEOUT_SECONDS)
        return None
    except Exception:
        logger.exception("S&P 500 batch: chunk download failed")
        return None
    finally:
        executor.shutdown(wait=False)


def run_sp500_batch(limit: int | None = None, period: str = "6mo") -> dict:
    """Refreshes S&P 500 membership and daily bars. Returns a summary dict for logging/manual-
    trigger API use.

    `period` defaults to "6mo" -- plenty for the routine daily run (SMA50/RSI14 only need ~50
    trading days, and price_history upserts on (symbol_id, timeframe, ts) so old rows are never
    pruned by a later smaller-period run). Pass a longer period (e.g. "5y") for a one-off deep
    backfill -- once seeded, the ordinary 6mo daily run keeps it current without re-requesting it.
    """
    constituents = get_sp500_constituents()
    if limit:
        constituents = constituents[:limit]
    tickers = [c["ticker"] for c in constituents]

    symbol_ids = mysql_writer.ensure_sp500_symbols(constituents)

    total_rows = 0
    failed_tickers: list[str] = []
    chunks = [tickers[i : i + CHUNK_SIZE] for i in range(0, len(tickers), CHUNK_SIZE)]

    # One connection reused for every insert in this run instead of opening a fresh one per
    # ticker (~500 potential connections otherwise) -- opening a new connection per call was
    # observed to add ~10s of latency *per call* in this environment, which is what actually made
    # the batch appear to hang, not the yfinance download step (see CHUNK_* comment above).
    db_conn = mysql_writer.connect()
    try:
        for chunk_index, chunk in enumerate(chunks):
            chunk_tickers = [t for t in chunk if symbol_ids.get(t) is not None]
            if not chunk_tickers:
                continue

            data = _download_chunk_with_timeout(chunk_tickers, period)
            if data is None:
                failed_tickers.extend(chunk_tickers)
            else:
                for ticker in chunk_tickers:
                    symbol_id = symbol_ids[ticker]
                    try:
                        history = data[ticker] if len(chunk_tickers) > 1 else data
                    except KeyError:
                        failed_tickers.append(ticker)
                        continue
                    rows = _rows_from_history(symbol_id, history)
                    if not rows:
                        failed_tickers.append(ticker)
                        continue
                    try:
                        total_rows += mysql_writer.insert_candles_bulk(rows, conn=db_conn)
                    except Exception:
                        # A single bad insert shouldn't abort the whole batch (this previously
                        # wasn't caught at all -- an exception here would propagate out and skip
                        # every remaining chunk, not just the rest of this ticker's work).
                        logger.exception("S&P 500 batch: failed to write candles for %s", ticker)
                        failed_tickers.append(ticker)

            if chunk_index < len(chunks) - 1:
                time.sleep(CHUNK_DELAY_SECONDS)
    finally:
        db_conn.close()

    logger.info(
        "S&P 500 batch: %d constituents, %d candle rows written, %d ticker(s) with no data",
        len(tickers),
        total_rows,
        len(failed_tickers),
    )
    return {"constituents": len(tickers), "candle_rows": total_rows, "failed_tickers": failed_tickers}
