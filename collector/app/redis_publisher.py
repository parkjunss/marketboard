import json
from datetime import datetime

import redis.asyncio as redis

from . import config

_client: redis.Redis | None = None


def get_client() -> redis.Redis:
    global _client
    if _client is None:
        _client = redis.Redis(host=config.REDIS_HOST, port=config.REDIS_PORT, decode_responses=True)
    return _client


async def publish_quote(symbol: str, price: float, volume: float, ts: datetime) -> None:
    client = get_client()
    payload = {"symbol": symbol, "price": price, "volume": volume, "ts": ts.isoformat()}
    await client.hset(f"quote:{symbol}", mapping={"price": str(price), "volume": str(volume), "ts": ts.isoformat()})
    await client.publish("quotes", json.dumps(payload))
