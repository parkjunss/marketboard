CREATE TABLE auto_trading_runs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    configuration_json LONGTEXT NOT NULL,
    prices_json LONGTEXT NOT NULL,
    state_json LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    cursor_position INT NOT NULL DEFAULT 0,
    bar_count INT NOT NULL,
    interval_seconds INT NOT NULL,
    next_tick_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_error VARCHAR(500),
    CONSTRAINT fk_auto_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT uk_auto_request UNIQUE(user_id, request_key),
    INDEX idx_auto_due(status, next_tick_at)
);

CREATE TABLE auto_trading_journal (
    run_id BIGINT NOT NULL,
    step_number INT NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    PRIMARY KEY(run_id, step_number),
    CONSTRAINT fk_auto_journal FOREIGN KEY(run_id) REFERENCES auto_trading_runs(id)
);

CREATE TABLE auto_trading_controls (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    action VARCHAR(24) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_auto_controls FOREIGN KEY(run_id) REFERENCES auto_trading_runs(id)
);
