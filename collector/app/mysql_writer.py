from datetime import datetime

import pymysql

from . import config

_INSERT_CANDLE_SQL = (
    "INSERT INTO price_history (symbol_id, ts, open, high, low, close, volume, timeframe) "
    "VALUES (%s,%s,%s,%s,%s,%s,%s,%s) "
    "ON DUPLICATE KEY UPDATE open=VALUES(open), high=VALUES(high), low=VALUES(low), "
    "close=VALUES(close), volume=VALUES(volume)"
)


def connect() -> pymysql.connections.Connection:
    return pymysql.connect(
        host=config.MYSQL_HOST,
        port=config.MYSQL_PORT,
        user=config.MYSQL_USER,
        password=config.MYSQL_PASSWORD,
        database=config.MYSQL_DATABASE,
        autocommit=True,
    )


def ensure_symbols(tickers: list[str]) -> dict[str, int]:
    """Idempotently insert tickers into `symbols` and return {ticker: id}."""
    if not tickers:
        return {}
    conn = connect()
    try:
        with conn.cursor() as cur:
            for ticker in tickers:
                cur.execute(
                    "INSERT IGNORE INTO symbols (ticker, name, exchange, is_active, priority) "
                    "VALUES (%s, %s, %s, TRUE, 0)",
                    (ticker, ticker, "US"),
                )
            placeholders = ",".join(["%s"] * len(tickers))
            cur.execute(f"SELECT id, ticker FROM symbols WHERE ticker IN ({placeholders})", tickers)
            return {ticker: symbol_id for symbol_id, ticker in cur.fetchall()}
    finally:
        conn.close()


def get_active_symbols() -> dict[str, int]:
    """Reads the current `is_active=TRUE` set from `symbols` -- the DB (kept in sync by the admin
    panel) is the source of truth for which tickers should be real-time WS-subscribed, so the
    collector should read it on startup rather than trusting a possibly-stale DEFAULT_SYMBOLS env var."""
    conn = connect()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, ticker FROM symbols WHERE is_active = TRUE")
            return {ticker: symbol_id for symbol_id, ticker in cur.fetchall()}
    finally:
        conn.close()


def ensure_sp500_symbols(constituents: list[dict]) -> dict[str, int]:
    """Idempotently upserts S&P 500 constituents into `symbols`, marked inactive by default and
    flagged `in_sp500_universe=TRUE`. Never touches `is_active` for symbols that already exist --
    joining the S&P 500 universe must not silently expand the real-time WS subscription set,
    which only `is_active` (set via the admin panel) controls."""
    if not constituents:
        return {}
    conn = connect()
    try:
        with conn.cursor() as cur:
            for c in constituents:
                cur.execute(
                    "INSERT INTO symbols (ticker, name, exchange, is_active, priority, in_sp500_universe) "
                    "VALUES (%s, %s, %s, FALSE, 0, TRUE) "
                    "ON DUPLICATE KEY UPDATE name=VALUES(name), in_sp500_universe=TRUE",
                    (c["ticker"], c["name"], "US"),
                )
            tickers = [c["ticker"] for c in constituents]
            placeholders = ",".join(["%s"] * len(tickers))
            cur.execute(f"SELECT id, ticker FROM symbols WHERE ticker IN ({placeholders})", tickers)
            return {ticker: symbol_id for symbol_id, ticker in cur.fetchall()}
    finally:
        conn.close()


def insert_candle(
    symbol_id: int,
    ts: datetime,
    open_: float,
    high: float,
    low: float,
    close: float,
    volume: float,
    timeframe: str,
) -> None:
    """Single-candle write for the live tick path (one call per symbol per minute — cheap
    enough to open a fresh connection each time). For bulk writes, use insert_candles_bulk."""
    conn = connect()
    try:
        with conn.cursor() as cur:
            cur.execute(_INSERT_CANDLE_SQL, (symbol_id, ts, open_, high, low, close, volume, timeframe))
    finally:
        conn.close()


def insert_candles_bulk(rows: list[tuple[int, datetime, float, float, float, float, float, str]]) -> int:
    """Batch-writes many candles over a single connection (e.g. yfinance backfill)."""
    if not rows:
        return 0
    conn = connect()
    try:
        with conn.cursor() as cur:
            cur.executemany(_INSERT_CANDLE_SQL, rows)
        return len(rows)
    finally:
        conn.close()
