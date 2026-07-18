from datetime import datetime, timezone

from app.aggregator import CandleAggregator


def _ts(second: int, minute: int = 0, hour: int = 12) -> datetime:
    return datetime(2026, 7, 16, hour, minute, second, tzinfo=timezone.utc)


def test_first_tick_opens_candle_and_returns_none():
    agg = CandleAggregator()
    result = agg.add_tick("AAPL", 100.0, 10, _ts(0))
    assert result is None


def test_ticks_within_same_minute_update_ohlcv_without_flushing():
    agg = CandleAggregator()
    agg.add_tick("AAPL", 100.0, 10, _ts(0))
    agg.add_tick("AAPL", 105.0, 5, _ts(20))
    result = agg.add_tick("AAPL", 98.0, 3, _ts(40))

    assert result is None
    candle = agg.flush_all()[0]
    assert candle.open == 100.0
    assert candle.high == 105.0
    assert candle.low == 98.0
    assert candle.close == 98.0
    assert candle.volume == 18


def test_tick_in_next_minute_flushes_previous_candle():
    agg = CandleAggregator()
    agg.add_tick("AAPL", 100.0, 10, _ts(0, minute=0))
    agg.add_tick("AAPL", 102.0, 5, _ts(30, minute=0))

    completed = agg.add_tick("AAPL", 110.0, 7, _ts(0, minute=1))

    assert completed is not None
    assert completed.symbol == "AAPL"
    assert completed.open == 100.0
    assert completed.high == 102.0
    assert completed.close == 102.0
    assert completed.volume == 15

    still_open = agg.flush_all()[0]
    assert still_open.open == 110.0
    assert still_open.volume == 7


def test_symbols_are_aggregated_independently():
    agg = CandleAggregator()
    agg.add_tick("AAPL", 100.0, 10, _ts(0))
    agg.add_tick("MSFT", 300.0, 2, _ts(1))
    agg.add_tick("AAPL", 101.0, 1, _ts(2))

    candles = {c.symbol: c for c in agg.flush_all()}
    assert candles["AAPL"].close == 101.0
    assert candles["AAPL"].volume == 11
    assert candles["MSFT"].close == 300.0
    assert candles["MSFT"].volume == 2


def test_late_out_of_order_tick_is_ignored_without_corrupting_current_candle():
    agg = CandleAggregator()
    agg.add_tick("AAPL", 100.0, 10, _ts(0, minute=1))
    result = agg.add_tick("AAPL", 999.0, 999, _ts(0, minute=0))

    assert result is None
    candle = agg.flush_all()[0]
    assert candle.open == 100.0
    assert candle.high == 100.0
    assert candle.volume == 10


def test_flush_all_clears_state():
    agg = CandleAggregator()
    agg.add_tick("AAPL", 100.0, 10, _ts(0))
    agg.flush_all()
    assert agg.flush_all() == []
