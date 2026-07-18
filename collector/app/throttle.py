import time


class Throttler:
    """Allows at most one `should_publish(key)` success per `interval_seconds`, per key."""

    def __init__(self, interval_seconds: float):
        self._interval = interval_seconds
        self._last: dict[str, float] = {}

    def should_publish(self, key: str, now: float | None = None) -> bool:
        now = time.monotonic() if now is None else now
        last = self._last.get(key, float("-inf"))
        if now - last < self._interval:
            return False
        self._last[key] = now
        return True
