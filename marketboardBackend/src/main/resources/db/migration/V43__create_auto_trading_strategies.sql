CREATE TABLE auto_trading_strategies (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    name VARCHAR(80) NOT NULL,
    source_run_id BIGINT NOT NULL,
    configuration_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_auto_strategy_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_auto_strategy_source FOREIGN KEY(source_run_id) REFERENCES auto_trading_runs(id),
    CONSTRAINT uk_auto_strategy_request UNIQUE(user_id, request_key)
);

ALTER TABLE auto_trading_runs ADD COLUMN strategy_id BIGINT NULL;
ALTER TABLE auto_trading_runs ADD CONSTRAINT fk_auto_run_strategy
    FOREIGN KEY(strategy_id) REFERENCES auto_trading_strategies(id);
