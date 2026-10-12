ALTER TABLE put_call_ratio_snapshot
    ADD COLUMN ratio_history_json TEXT NULL;

UPDATE put_call_ratio_snapshot SET ratio_history_json = '[]';

ALTER TABLE put_call_ratio_snapshot
    MODIFY COLUMN ratio_history_json TEXT NOT NULL;
