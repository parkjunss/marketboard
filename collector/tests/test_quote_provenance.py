import importlib.util
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
import app


@pytest.mark.asyncio
@pytest.mark.parametrize("source,observed", [
    ("FINNHUB", datetime(2026, 9, 10, 14, tzinfo=timezone.utc)),
    ("YFINANCE", None),
])
async def test_publisher_separates_observed_and_fetched_time(monkeypatch, source, observed):
    # Load in isolation without production config/secrets or Redis connections.
    config = SimpleNamespace()
    monkeypatch.setitem(sys.modules, "app.config", config)
    monkeypatch.setattr(app, "config", config, raising=False)
    spec = importlib.util.spec_from_file_location("app._publisher_test", Path(__file__).parents[1] / "app/redis_publisher.py")
    publisher = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(publisher)
    client = AsyncMock()
    publisher.get_client = lambda: client
    await publisher.publish_quote("AAA", 100., 20., observed, source=source)
    cached = client.hset.call_args.kwargs["mapping"]
    payload = json.loads(client.publish.call_args.args[1])
    assert cached["source"] == payload["source"] == source
    assert cached["fetchedAt"] == payload["fetchedAt"]
    assert datetime.fromisoformat(payload["fetchedAt"]).tzinfo is not None
    assert payload["ts"] == (observed.isoformat() if observed else None)
    assert cached["ts"] == (observed.isoformat() if observed else "")
