from dataclasses import dataclass, field
from datetime import datetime


@dataclass
class CollectorState:
    subscribed_symbols: set[str] = field(default_factory=set)
    ws_connected: bool = False
    reconnect_count: int = 0
    last_tick_at: dict[str, datetime] = field(default_factory=dict)
    last_error: str | None = None


state = CollectorState()
