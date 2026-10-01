from datetime import date, datetime, timezone
from unittest.mock import MagicMock

import pandas as pd
import pytest

from app import screening_snapshot


def _prices(count=300):
    return pd.Series([100 * 1.001**day for day in range(count)])


def test_technical_snapshot_contains_fixed_metrics():
    metrics = screening_snapshot._technical_metrics(_prices())

    assert metrics.keys() == {
        "price", "momentum3m", "momentum6m", "momentum12m", "volatility20d",
        "rsi14", "sma50", "sma100", "sma200", "aboveSma200",
    }
    assert metrics["momentum3m"] > 0
    assert metrics["aboveSma200"] is True


def test_technical_snapshot_skips_insufficient_history():
    assert screening_snapshot._technical_metrics(_prices(252)) is None


def _row():
    return {
        "ticker": "AAA", "price": 123.0, "momentum3m": 1.0, "momentum6m": 2.0,
        "momentum12m": 3.0, "volatility20d": 4.0, "rsi14": 50.0, "sma50": 110.0,
        "sma100": 105.0, "sma200": 100.0, "aboveSma200": True, "marketCap": 1_000,
        "totalRevenue": 500, "revenueGrowthPct": 5.0, "returnOnEquityPct": 6.0,
        "profitMarginPct": 7.0, "trailingPE": 20.0, "newsSentiment": 0.1, "newsCount": 2,
    }


def test_persist_bulk_inserts_and_completes_run(monkeypatch):
    conn = MagicMock()
    cursor = conn.cursor.return_value.__enter__.return_value
    cursor.lastrowid = 42
    cursor.fetchall.return_value = [(7, "AAA")]
    monkeypatch.setattr(screening_snapshot.mysql_writer, "connect", lambda: conn)

    run_id, count = screening_snapshot._persist(42, date(2026, 10, 1), datetime.now(timezone.utc), [_row(), _row()])

    assert (run_id, count) == (42, 2)
    cursor.executemany.assert_called_once()
    assert "ON DUPLICATE KEY UPDATE" in cursor.executemany.call_args.args[0]
    assert any("status='COMPLETED'" in call.args[0] for call in cursor.execute.call_args_list)
    conn.commit.assert_called_once()


def test_persist_marks_run_failed_without_deleting_previous_snapshot(monkeypatch):
    conn = MagicMock()
    cursor = conn.cursor.return_value.__enter__.return_value
    cursor.fetchall.return_value = [(7, "AAA")]
    cursor.executemany.side_effect = RuntimeError("write failed")
    monkeypatch.setattr(screening_snapshot.mysql_writer, "connect", lambda: conn)

    with pytest.raises(RuntimeError, match="write failed"):
        screening_snapshot._persist(43, date(2026, 10, 1), datetime.now(timezone.utc), [_row()])

    conn.rollback.assert_called_once()
    assert not any("DELETE" in call.args[0].upper() for call in cursor.execute.call_args_list)


def test_batch_marks_run_failed_when_calculation_fails(monkeypatch):
    monkeypatch.setattr(screening_snapshot, "_start_run", lambda started: 44)
    monkeypatch.setattr(screening_snapshot, "_load_universe_closes", MagicMock(side_effect=RuntimeError("load failed")))
    mark_failed = MagicMock()
    monkeypatch.setattr(screening_snapshot, "_mark_failed", mark_failed)

    with pytest.raises(RuntimeError, match="load failed"):
        screening_snapshot.run_snapshot_batch()

    mark_failed.assert_called_once()
    assert mark_failed.call_args.args[0] == 44
