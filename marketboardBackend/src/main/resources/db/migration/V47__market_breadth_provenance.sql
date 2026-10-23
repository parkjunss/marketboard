-- Legacy snapshot_date is a computation date, not a verified exchange trading date.
ALTER TABLE market_breadth_snapshots
    ADD COLUMN trading_date DATE NULL,
    ADD COLUMN source VARCHAR(100) NULL,
    ADD COLUMN calendar_name VARCHAR(20) NULL,
    ADD COLUMN calendar_version VARCHAR(40) NULL,
    ADD COLUMN session_closed_at TIMESTAMP(6) NULL,
    ADD COLUMN eligible_count INT NULL;
