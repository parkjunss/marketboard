import json
from datetime import datetime, timezone

import redis.asyncio as redis

from . import config

_client: redis.Redis | None = None


def get_client() -> redis.Redis:
    global _client
    if _client is None:
        _client = redis.Redis(host=config.REDIS_HOST, port=config.REDIS_PORT, decode_responses=True)
    return _client


async def publish_quote(symbol: str, price: float, volume: float, ts: datetime | None, *, source: str = "FINNHUB") -> None:
    client = get_client()
    fetched_at = datetime.now(timezone.utc).isoformat()
    observed_at = ts.isoformat() if ts is not None else None
    payload = {"symbol": symbol, "price": price, "volume": volume, "ts": observed_at,
               "source": source, "fetchedAt": fetched_at}
    # Empty ts explicitly clears a previous tick's timestamp on a REST fallback update.
    await client.hset(f"quote:{symbol}", mapping={"price": str(price), "volume": str(volume),
                       "ts": observed_at or "", "source": source, "fetchedAt": fetched_at})
    await client.publish("quotes", json.dumps(payload))
