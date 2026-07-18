import asyncio
import json
import logging
from datetime import datetime, timezone
from typing import Awaitable, Callable

import websockets
from websockets.exceptions import ConnectionClosed

from . import config
from .state import state

logger = logging.getLogger("collector.finnhub")

TickHandler = Callable[[str, float, float, datetime], Awaitable[None]]
SymbolsProvider = Callable[[], set[str]]


class FinnhubWebSocketSource:
    """Connects to Finnhub's trade WebSocket, keeps subscriptions in sync with
    `symbols_provider`, and calls `on_tick` for every trade received.

    Reconnects with exponential backoff on any connection drop. Throttling and
    aggregation are the caller's responsibility (`on_tick` receives every tick).
    """

    def __init__(self, on_tick: TickHandler, symbols_provider: SymbolsProvider):
        self._on_tick = on_tick
        self._symbols_provider = symbols_provider
        self._ws = None
        self._stop = False
        self._subscribed: set[str] = set()

    async def run(self) -> None:
        backoff = 1.0
        max_backoff = 60.0
        while not self._stop:
            try:
                # Finnhub's WS server doesn't reliably answer protocol-level ping frames,
                # so client-side keepalive pings (ping_interval) cause false-positive
                # disconnects even while trade data keeps flowing normally. Disabled —
                # dead connections still surface as ConnectionClosed/OSError from the
                # message-read loop below.
                async with websockets.connect(config.FINNHUB_WS_URL, ping_interval=None) as ws:
                    self._ws = ws
                    state.ws_connected = True
                    state.last_error = None
                    backoff = 1.0
                    await self._resubscribe_all()
                    async for raw in ws:
                        await self._handle_message(raw)
            except (ConnectionClosed, OSError) as exc:
                state.reconnect_count += 1
                state.last_error = str(exc)
                logger.warning("Finnhub WS disconnected (%s); reconnecting in %.1fs", exc, backoff)
            finally:
                self._ws = None
                state.ws_connected = False
            if self._stop:
                break
            await asyncio.sleep(backoff)
            backoff = min(backoff * 2, max_backoff)

    async def stop(self) -> None:
        self._stop = True
        if self._ws is not None:
            await self._ws.close()

    async def sync_subscriptions(self) -> None:
        """Reconcile live WS subscriptions with symbols_provider() — call after it changes."""
        wanted = self._symbols_provider()
        for symbol in wanted - self._subscribed:
            await self._subscribe(symbol)
        for symbol in self._subscribed - wanted:
            await self._unsubscribe(symbol)

    async def _resubscribe_all(self) -> None:
        self._subscribed.clear()
        for symbol in self._symbols_provider():
            await self._subscribe(symbol)

    async def _subscribe(self, symbol: str) -> None:
        if self._ws is None or symbol in self._subscribed:
            return
        await self._ws.send(json.dumps({"type": "subscribe", "symbol": symbol}))
        self._subscribed.add(symbol)

    async def _unsubscribe(self, symbol: str) -> None:
        if self._ws is None or symbol not in self._subscribed:
            return
        await self._ws.send(json.dumps({"type": "unsubscribe", "symbol": symbol}))
        self._subscribed.discard(symbol)
        state.last_tick_at.pop(symbol, None)

    async def _handle_message(self, raw: str) -> None:
        try:
            msg = json.loads(raw)
        except json.JSONDecodeError:
            return
        if msg.get("type") != "trade":
            return
        for trade in msg.get("data", []):
            symbol = trade.get("s")
            price = trade.get("p")
            ts_ms = trade.get("t")
            if symbol is None or price is None or ts_ms is None:
                continue
            volume = trade.get("v", 0)
            ts = datetime.fromtimestamp(ts_ms / 1000, tz=timezone.utc)
            await self._on_tick(symbol, price, volume, ts)
