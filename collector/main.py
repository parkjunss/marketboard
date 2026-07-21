import asyncio
import logging
from contextlib import asynccontextmanager
from datetime import date, datetime

import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app import config, mysql_writer
from app.aggregator import CandleAggregator
from app.alerts import check_alerts
from app.backfill import backfill_symbol
from app.backtest import InsufficientDataError, run_backtest
from app.financials import get_financials
from app.finnhub_source import FinnhubWebSocketSource
from app.market_indices import get_index_history, list_indices
from app.news import get_company_news, get_general_news
from app.redis_publisher import publish_quote
from app.rest_fallback import rest_fallback_loop
from app.sentiment import FearGreedUnavailableError, PutCallDataUnavailableError, get_fear_greed, get_put_call_ratio
from app.sp500_universe import run_sp500_batch
from app.state import state
from app.symbol_profile import UnknownSymbolError, get_symbol_profile
from app.throttle import Throttler

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("collector")

aggregator = CandleAggregator()
throttler = Throttler(config.TICK_THROTTLE_SECONDS)
_symbol_ids: dict[str, int] = {}


def get_active_symbols() -> set[str]:
    return set(state.subscribed_symbols)


async def handle_tick(symbol: str, price: float, volume: float, ts: datetime) -> None:
    state.last_tick_at[symbol] = ts

    completed = aggregator.add_tick(symbol, price, volume, ts)
    if completed is not None:
        symbol_id = _symbol_ids.get(completed.symbol)
        if symbol_id is not None:
            await asyncio.to_thread(
                mysql_writer.insert_candle,
                symbol_id,
                completed.minute,
                completed.open,
                completed.high,
                completed.low,
                completed.close,
                completed.volume,
                "1m",
            )

    await check_alerts(symbol, price)

    if throttler.should_publish(symbol):
        await publish_quote(symbol, price, volume, ts)


source = FinnhubWebSocketSource(on_tick=handle_tick, symbols_provider=get_active_symbols)


async def sp500_batch_loop():
    """Runs once at startup, then once a day -- S&P 500 membership and daily bars don't change
    more often than that, unlike the real-time WS tick path this loop is otherwise independent of."""
    while True:
        try:
            await asyncio.to_thread(run_sp500_batch, config.SP500_BATCH_LIMIT)
        except Exception:
            logger.exception("S&P 500 batch failed")
        await asyncio.sleep(config.SP500_BATCH_INTERVAL_SECONDS)


def _refresh_active_symbols_daily_bars() -> None:
    active = mysql_writer.get_active_symbols()
    for ticker, symbol_id in active.items():
        try:
            # Short period -- these tickers already have deep history from a one-time backfill
            # (or the S&P 500 batch); this just needs to catch up the last few trading days.
            backfill_symbol(ticker, symbol_id, period="5d")
        except Exception:
            logger.exception("Daily bar refresh failed for %s", ticker)


async def active_symbols_daily_refresh_loop():
    """Keeps the real-time WS symbol set's daily bars current, independent of S&P 500 batch
    coverage -- index ETF proxies (SPY/QQQ/DIA) aren't S&P 500 constituents and would otherwise
    never get refreshed, and any active ticker could otherwise lag behind if the (much larger)
    S&P 500 batch hasn't reached it alphabetically yet. Runs once at startup, then daily."""
    while True:
        try:
            await asyncio.to_thread(_refresh_active_symbols_daily_bars)
        except Exception:
            logger.exception("Active symbols daily refresh failed")
        await asyncio.sleep(config.ACTIVE_SYMBOLS_REFRESH_INTERVAL_SECONDS)


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _symbol_ids
    # The DB's `is_active` set (managed via the admin panel) is the source of truth for which
    # tickers should be real-time WS-subscribed -- only fall back to DEFAULT_SYMBOLS to bootstrap
    # a brand-new DB that has no active symbols yet.
    active_symbols = await asyncio.to_thread(mysql_writer.get_active_symbols)
    if active_symbols:
        _symbol_ids = active_symbols
        state.subscribed_symbols = set(active_symbols.keys())
    else:
        state.subscribed_symbols = set(config.DEFAULT_SYMBOLS)
        _symbol_ids = await asyncio.to_thread(mysql_writer.ensure_symbols, config.DEFAULT_SYMBOLS)

    ws_task = asyncio.create_task(source.run())
    fallback_task = asyncio.create_task(rest_fallback_loop(get_active_symbols))
    sp500_task = asyncio.create_task(sp500_batch_loop())
    active_refresh_task = asyncio.create_task(active_symbols_daily_refresh_loop())
    try:
        yield
    finally:
        await source.stop()
        active_refresh_task.cancel()
        sp500_task.cancel()
        fallback_task.cancel()
        ws_task.cancel()


app = FastAPI(title="MarketBoard Collector", lifespan=lifespan)


@app.get("/health")
async def health():
    return {
        "ws_connected": state.ws_connected,
        "reconnect_count": state.reconnect_count,
        "subscribed_symbols": sorted(state.subscribed_symbols),
        "last_tick_at": {s: t.isoformat() for s, t in state.last_tick_at.items()},
        "last_error": state.last_error,
    }


class SubscriptionUpdate(BaseModel):
    symbols: list[str]


@app.get("/subscriptions")
async def get_subscriptions():
    return {"symbols": sorted(state.subscribed_symbols)}


@app.put("/subscriptions")
async def update_subscriptions(update: SubscriptionUpdate):
    global _symbol_ids
    tickers = [s.strip().upper() for s in update.symbols if s.strip()]

    new_ids = await asyncio.to_thread(mysql_writer.ensure_symbols, tickers)
    _symbol_ids.update(new_ids)
    state.subscribed_symbols = set(tickers)
    await source.sync_subscriptions()

    return {"symbols": sorted(state.subscribed_symbols)}


@app.get("/news")
async def news_general():
    try:
        return await asyncio.to_thread(get_general_news)
    except requests.RequestException as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/news/{symbol}")
async def news_company(symbol: str):
    try:
        return await asyncio.to_thread(get_company_news, symbol)
    except requests.RequestException as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/market-indices")
async def market_indices_list():
    return list_indices()


@app.get("/market-indices/{slug}/history")
async def market_indices_history(slug: str, period: str = "6mo"):
    try:
        return await asyncio.to_thread(get_index_history, slug.upper(), period)
    except KeyError:
        raise HTTPException(status_code=404, detail=f"Unknown index {slug}")


@app.get("/financials/{symbol}")
async def financials(symbol: str):
    try:
        return await asyncio.to_thread(get_financials, symbol.upper())
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/symbol-profile/{symbol}")
async def symbol_profile(symbol: str):
    try:
        return await asyncio.to_thread(get_symbol_profile, symbol.upper())
    except UnknownSymbolError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


class BacktestRunPayload(BaseModel):
    tickers: list[str]
    startDate: date
    endDate: date
    initialCapital: float
    riskFreeRate: float


@app.post("/backtest/run")
async def backtest_run(payload: BacktestRunPayload):
    try:
        return await asyncio.to_thread(
            run_backtest, payload.tickers, payload.startDate, payload.endDate, payload.initialCapital, payload.riskFreeRate
        )
    except InsufficientDataError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/sentiment/fear-greed")
async def sentiment_fear_greed():
    try:
        return await asyncio.to_thread(get_fear_greed)
    except FearGreedUnavailableError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/sentiment/put-call-ratio")
async def sentiment_put_call_ratio(ticker: str = "SPY"):
    try:
        return await asyncio.to_thread(get_put_call_ratio, ticker.upper())
    except PutCallDataUnavailableError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/backfill/{ticker}")
async def backfill_ticker(ticker: str, period: str = "5y"):
    """One-off deep backfill for a single arbitrary ticker -- for symbols outside the S&P 500
    universe (e.g. index ETFs like SPY/QQQ/DIA) that never get touched by sp500_batch_loop, and
    that active_symbols_daily_refresh_loop only ever catches up with a shallow period="5d" (it
    assumes deep history already exists). A freshly-activated symbol with zero price_history rows
    won't show up in the frontend's stock list at all (its history fetch returns empty), so this
    is normally needed right after activating a new symbol that isn't an S&P 500 constituent."""
    ticker = ticker.upper()
    try:
        symbol_ids = await asyncio.to_thread(mysql_writer.ensure_symbols, [ticker])
        rows = await asyncio.to_thread(backfill_symbol, ticker, symbol_ids[ticker], period)
        return {"ticker": ticker, "period": period, "rows": rows}
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/sp500/sync")
async def sp500_sync(limit: int | None = None, period: str = "6mo"):
    """Manual trigger for the S&P 500 batch (also runs automatically, see sp500_batch_loop).
    Pass period="5y" for a one-off deep backfill of the whole universe beyond the routine 6mo
    window (see run_sp500_batch's docstring)."""
    try:
        return await asyncio.to_thread(run_sp500_batch, limit, period)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
