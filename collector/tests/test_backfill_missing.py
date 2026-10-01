from app import backfill


def test_backfill_missing_only_processes_symbols_without_daily_history(monkeypatch):
    monkeypatch.setattr(backfill.mysql_writer, "get_symbols_missing_daily_history", lambda: {"AAA": 1, "BAD": 2})
    monkeypatch.setattr(backfill, "backfill_symbol", lambda ticker, symbol_id, period: 10 if ticker == "AAA" else 0)

    result = backfill.backfill_missing_symbols("5y")

    assert result == {"attempted": 2, "succeeded": 1, "failed": ["BAD"], "totalRows": 10}
