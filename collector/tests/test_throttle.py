from app.throttle import Throttler


def test_first_call_always_allowed():
    throttler = Throttler(interval_seconds=1.0)
    assert throttler.should_publish("AAPL", now=100.0) is True


def test_call_within_interval_is_rejected():
    throttler = Throttler(interval_seconds=1.0)
    throttler.should_publish("AAPL", now=100.0)
    assert throttler.should_publish("AAPL", now=100.5) is False


def test_call_after_interval_is_allowed():
    throttler = Throttler(interval_seconds=1.0)
    throttler.should_publish("AAPL", now=100.0)
    assert throttler.should_publish("AAPL", now=101.1) is True


def test_keys_are_throttled_independently():
    throttler = Throttler(interval_seconds=1.0)
    throttler.should_publish("AAPL", now=100.0)
    assert throttler.should_publish("MSFT", now=100.0) is True
