CREATE TABLE execution_runs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    configuration_json LONGTEXT NOT NULL,
    state_json LONGTEXT NOT NULL,
    cash DECIMAL(28,8) NOT NULL,
    reserved_cash DECIMAL(28,8) NOT NULL,
    shares DECIMAL(28,8) NOT NULL,
    reserved_shares DECIMAL(28,8) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_execution_balances CHECK (cash >= reserved_cash AND reserved_cash >= 0 AND shares >= reserved_shares AND reserved_shares >= 0),
    CONSTRAINT uk_execution_start UNIQUE (user_id, request_key),
    CONSTRAINT fk_execution_owner FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE execution_commands (
    run_id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    command_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id, sequence_no),
    CONSTRAINT uk_execution_command UNIQUE (run_id, request_key),
    CONSTRAINT fk_execution_command_run FOREIGN KEY (run_id) REFERENCES execution_runs(id) ON DELETE CASCADE
);
