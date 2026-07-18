"""Backfill daily OHLCV history for tracked symbols via yfinance.

Usage:
    uv run python -m app.backfill              # backfills config.DEFAULT_SYMBOLS
    uv run python -m app.backfill AAPL MSFT    # backfills specific tickers
"""

import sys
from datetime import timezone

import yfinance as yf

from . import config, mysql_writer


def backfill_symbol(ticker: str, symbol_id: int, period: str = "5y") -> int:
    data = yf.Ticker(ticker).history(period=period, interval="1d")
    rows = [
        (
            symbol_id,
            idx.to_pydatetime().astimezone(timezone.utc),
            float(row["Open"]),
            float(row["High"]),
            float(row["Low"]),
            float(row["Close"]),
            int(row["Volume"]),
            "1d",
        )
        for idx, row in data.iterrows()
    ]
    return mysql_writer.insert_candles_bulk(rows)


def main(tickers: list[str] | None = None) -> None:
    tickers = tickers or config.DEFAULT_SYMBOLS
    symbol_ids = mysql_writer.ensure_symbols(tickers)
    for ticker in tickers:
        n = backfill_symbol(ticker, symbol_ids[ticker])
        print(f"{ticker}: backfilled {n} daily bars")


if __name__ == "__main__":
    main(sys.argv[1:] or None)
