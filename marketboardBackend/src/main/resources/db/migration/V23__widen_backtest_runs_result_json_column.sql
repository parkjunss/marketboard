-- TEXT tops out at ~64KB, which a multi-year daily equity curve (~75 bytes/day * 2 series) can
-- exceed well before a decade of history -- e.g. a 5-year backtest already overflows it.
-- MEDIUMTEXT's 16MB ceiling comfortably covers even multi-decade daily backtests.
ALTER TABLE backtest_runs MODIFY COLUMN result_json MEDIUMTEXT;
