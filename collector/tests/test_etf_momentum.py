from copy import deepcopy
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
import json
import sys
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

from app import etf_momentum as strategy


@pytest.fixture
def histories():
    result = {}
    for index, ticker in enumerate(strategy.ETF_UNIVERSE):
        bars = []
        day = date(2024, 8, 1)
        while day <= date(2025, 10, 15):
            if day.weekday() < 5:
                bars.append((datetime(day.year, day.month, day.day, 16, tzinfo=timezone.utc),
                             100 + len(bars) * (6 - index) / 100))
            day += timedelta(days=1)
        result[ticker] = bars
    return result


AS_OF = date(2025, 9, 30)


def test_month_end_uses_new_york_and_handles_leap_year():
    assert strategy.last_completed_month(datetime(2026, 10, 1, 2, tzinfo=timezone.utc)) == date(2026, 8, 31)
    assert strategy.last_completed_month(datetime(2024, 3, 1, 12, tzinfo=timezone.utc)) == date(2024, 2, 29)
    assert strategy.build_etf_momentum({}, date(2024, 2, 29))["startDate"] == "2023-02-28"
    with pytest.raises(ValueError, match="월말"):
        strategy.build_etf_momentum({}, date(2025, 9, 29))


def test_rank_then_trend_filter_does_not_replace_failed_top_three(histories):
    before = strategy.build_etf_momentum(histories, AS_OF)
    assert before["ready"]
    assert [row["ticker"] for row in before["rows"][:3]] == ["SPY", "QQQ", "EFA"]
    prices = {ts.date(): close for ts, close in histories["SPY"]}
    assert before["rows"][0]["momentum"] == pytest.approx((prices[date(2025, 8, 29)] / prices[date(2024, 9, 30)] - 1) * 100)
    assert before["cashWeight"] == 0
    histories["SPY"] = [(ts, 1 if ts.date() >= date(2025, 9, 1) else close) for ts, close in histories["SPY"]]
    after = strategy.build_etf_momentum(histories, AS_OF)
    assert after["rows"][0]["momentum"] == before["rows"][0]["momentum"]
    assert after["rows"][0]["weight"] == 0
    assert after["rows"][3]["weight"] == 0
    assert after["cashWeight"] == pytest.approx(1 / 3)
    assert after["rows"][0]["sessionDate"] == "2025-09-30"
    assert sum(row["weight"] for row in after["rows"]) + after["cashWeight"] == pytest.approx(1)


def test_future_bars_are_ignored_and_sma_uses_exactly_last_200_bars(histories):
    result = strategy.build_etf_momentum(histories, AS_OF)
    eligible = [close for ts, close in histories["SPY"] if ts.date() <= AS_OF]
    assert result["rows"][0]["sma200"] == pytest.approx(sum(eligible[-200:]) / 200)
    histories["SPY"] = [(ts, float("nan") if ts.date() > AS_OF else close) for ts, close in histories["SPY"]]
    assert strategy.build_etf_momentum(histories, AS_OF) == result


def test_ties_use_ticker_order_and_equal_sma_keeps_cash(histories):
    constant = {ticker: [(ts, Decimal("100")) for ts, _ in bars] for ticker, bars in histories.items()}
    result = strategy.build_etf_momentum(constant, AS_OF)
    assert result["ready"]
    assert [row["ticker"] for row in result["rows"][:3]] == ["EEM", "EFA", "GLD"]
    assert result["cashWeight"] == 1


@pytest.mark.parametrize("invalid", [None, 0, -1, float("nan"), float("inf"), "bad"])
def test_invalid_prices_withhold_all_weights(histories, invalid):
    ts, _ = histories["GLD"][30]
    histories["GLD"][30] = (ts, invalid)
    result = strategy.build_etf_momentum(histories, AS_OF)
    assert not result["ready"]
    assert result["cashWeight"] is None
    assert all(row["rank"] is None and row["weight"] is None for row in result["rows"])
    json.dumps(result, allow_nan=False)


@pytest.mark.parametrize("problem", ["missing", "short", "invalid_date", "duplicate", "stale", "mismatch", "gap"])
def test_bad_history_does_not_become_a_cash_signal(histories, problem):
    bars = histories["GLD"]
    if problem == "missing":
        del histories["GLD"]
    elif problem == "short":
        histories["GLD"] = bars[-100:]
    elif problem == "invalid_date":
        bars[30] = (None, 100)
    elif problem == "duplicate":
        bars.append(bars[30])
    elif problem == "stale":
        histories["GLD"] = [(ts, close) for ts, close in bars if ts.date() < date(2025, 9, 1)]
    elif problem == "mismatch":
        del bars[100]
    else:
        del bars[100:120]
    result = strategy.build_etf_momentum(histories, AS_OF)
    assert not result["ready"]
    assert result["cashWeight"] is None
    assert all(row["rank"] is None and row["weight"] is None and row["reason"] for row in result["rows"])


def test_sorting_does_not_mutate_input_and_naive_utc_matches_aware(histories):
    expected = strategy.build_etf_momentum(histories, AS_OF)
    histories["SPY"].reverse()
    original = deepcopy(histories)
    assert strategy.build_etf_momentum(histories, AS_OF) == expected
    assert histories == original
    naive = {ticker: [(ts.replace(tzinfo=None), close) for ts, close in bars] for ticker, bars in histories.items()}
    assert strategy.build_etf_momentum(naive, AS_OF) == expected


def test_gap_preserves_metrics_from_observed_bars_without_inventing_weights(histories):
    del histories["SPY"][100:120]
    result = strategy.build_etf_momentum(histories, AS_OF)
    row = next(row for row in result["rows"] if row["ticker"] == "SPY")
    bars = [(ts.date(), close) for ts, close in histories["SPY"] if ts.date() <= AS_OF]
    prices = dict(bars)
    assert row["close"] == bars[-1][1]
    assert row["sessionDate"] == AS_OF.isoformat()
    assert row["sma200"] == pytest.approx(sum(close for _, close in bars[-200:]) / 200)
    assert row["momentum"] == pytest.approx((prices[date(2025, 8, 29)] / prices[date(2024, 9, 30)] - 1) * 100)
    assert "7일 초과 공백" in row["reason"]
    assert not result["ready"] and row["weight"] is None


def test_short_history_keeps_close_but_does_not_label_short_average_as_sma200(histories):
    histories["SPY"] = histories["SPY"][-100:]
    result = strategy.build_etf_momentum(histories, AS_OF)
    row = next(row for row in result["rows"] if row["ticker"] == "SPY")
    assert row["close"] is not None
    assert row["sessionDate"] == AS_OF.isoformat()
    assert row["sma200"] is None and row["momentum"] is None
    assert not result["ready"] and row["weight"] is None


def test_run_uses_one_server_cutoff_for_data_and_calculation(monkeypatch, histories):
    monkeypatch.setattr(strategy, "last_completed_month", lambda: AS_OF)
    def load(as_of):
        assert as_of == AS_OF
        return histories
    monkeypatch.setattr(strategy, "_load_histories", load)
    assert strategy.run_etf_momentum() == strategy.build_etf_momentum(histories, AS_OF)


def mock_database(monkeypatch):
    import app
    conn = MagicMock()
    writer = SimpleNamespace(connect=lambda: conn)
    monkeypatch.setitem(sys.modules, "app.mysql_writer", writer)
    monkeypatch.setattr(app, "mysql_writer", writer, raising=False)
    return conn, conn.cursor.return_value.__enter__.return_value


def test_loader_is_read_only_bounded_and_preserves_missing_symbols(monkeypatch):
    conn, cursor = mock_database(monkeypatch)
    timestamp = datetime(2025, 9, 30, 4)
    cursor.fetchall.side_effect = [[(1, "SPY"), (2, "GLD")], [(1, timestamp, Decimal("199"), Decimal("200"))]]
    assert strategy._load_histories(AS_OF) == {"SPY": [(timestamp, Decimal("200"))], "GLD": []}
    sql, params = cursor.execute.call_args.args
    assert "symbol_id IN (%s,%s)" in sql
    assert "timeframe = '1d'" in sql and "ts >= %s AND ts < %s" in sql
    assert params == (1, 2, datetime(2024, 9, 23, 4), datetime(2025, 10, 1, 4))
    conn.close.assert_called_once()


def test_loader_closes_on_database_error_and_does_not_hide_it(monkeypatch):
    conn, cursor = mock_database(monkeypatch)
    cursor.execute.side_effect = RuntimeError("database unavailable")
    with pytest.raises(RuntimeError, match="database unavailable"):
        strategy._load_histories(AS_OF)
    conn.close.assert_called_once()
