CREATE TABLE auto_trading_validations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    strategy_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    result_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_auto_validation_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_auto_validation_strategy FOREIGN KEY(strategy_id) REFERENCES auto_trading_strategies(id),
    CONSTRAINT uk_auto_validation_request UNIQUE(user_id, request_key)
);
