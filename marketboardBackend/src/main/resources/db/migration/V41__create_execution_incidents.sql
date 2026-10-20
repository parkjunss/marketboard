CREATE TABLE execution_incidents (
    run_id BIGINT NOT NULL,
    kind VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL,
    opened_at TIMESTAMP(6) NOT NULL,
    resolved_at TIMESTAMP(6) NULL,
    occurrences BIGINT NOT NULL DEFAULT 1,
    message VARCHAR(500) NOT NULL,
    PRIMARY KEY (run_id, kind),
    CONSTRAINT fk_execution_incident_run FOREIGN KEY (run_id) REFERENCES execution_runs(id) ON DELETE CASCADE
);
