-- The single existing row represents SPY (PutCallRatioService's market-wide sentiment card), so
-- backfilling the new column with that default preserves its identity when this migration runs
-- against an environment that already has that row.
ALTER TABLE put_call_ratio_snapshot ADD COLUMN ticker VARCHAR(20) NOT NULL DEFAULT 'SPY';
ALTER TABLE put_call_ratio_snapshot ADD CONSTRAINT uk_put_call_ratio_snapshot_ticker UNIQUE (ticker);
