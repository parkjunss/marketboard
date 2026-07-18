from dataclasses import dataclass
from datetime import datetime, timezone


@dataclass
class Candle:
    symbol: str
    minute: datetime
    open: float
    high: float
    low: float
    close: float
    volume: float


class CandleAggregator:
    """Buckets ticks into per-symbol 1-minute OHLCV candles.

    A completed candle is only returned once a tick from the *next* minute
    arrives for that symbol — there is no wall-clock timer, so a symbol with
    no ticks in a given minute simply produces no candle for it.
    """

    def __init__(self):
        self._current: dict[str, Candle] = {}

    @staticmethod
    def _minute_bucket(ts: datetime) -> datetime:
        return ts.astimezone(timezone.utc).replace(second=0, microsecond=0)

    def add_tick(self, symbol: str, price: float, volume: float, ts: datetime) -> Candle | None:
        bucket = self._minute_bucket(ts)
        current = self._current.get(symbol)

        if current is None:
            self._current[symbol] = Candle(symbol, bucket, price, price, price, price, volume)
            return None

        if bucket > current.minute:
            completed = current
            self._current[symbol] = Candle(symbol, bucket, price, price, price, price, volume)
            return completed

        if bucket < current.minute:
            return None

        current.high = max(current.high, price)
        current.low = min(current.low, price)
        current.close = price
        current.volume += volume
        return None

    def flush_all(self) -> list[Candle]:
        candles = list(self._current.values())
        self._current.clear()
        return candles
