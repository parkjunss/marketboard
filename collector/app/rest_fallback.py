import asyncio
import logging
from datetime import datetime, timezone

import yfinance as yf

from . import config
from .redis_publisher import publish_quote
from .state import state

logger = logging.getLogger("collector.rest_fallback")


def _fetch_quote(symbol: str) -> tuple[float | None, float]:
    fast_info = yf.Ticker(symbol).fast_info
    return fast_info.last_price, fast_info.last_volume or 0


async def rest_fallback_loop(symbols_provider) -> None:
    """Polls yfinance for any symbol whose WS ticks have gone stale (e.g. outside
    US market hours, when no trades occur). Runs forever until cancelled.
    """
    while True:
        await asyncio.sleep(config.REST_FALLBACK_POLL_SECONDS)
        now = datetime.now(timezone.utc)
        for symbol in symbols_provider():
            last_tick = state.last_tick_at.get(symbol)
            if last_tick is not None and (now - last_tick).total_seconds() < config.REST_FALLBACK_STALE_AFTER_SECONDS:
                continue
            try:
                price, volume = await asyncio.to_thread(_fetch_quote, symbol)
            except Exception as exc:
                logger.warning("REST fallback failed for %s: %s", symbol, exc)
                continue
            if not price:
                continue
            await publish_quote(symbol, price, volume, now)
            state.last_tick_at[symbol] = now
