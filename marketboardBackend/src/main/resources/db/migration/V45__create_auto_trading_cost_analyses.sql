CREATE TABLE auto_trading_cost_analyses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    validation_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    scenarios_json LONGTEXT NOT NULL,
    results_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_auto_cost_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_auto_cost_validation FOREIGN KEY(validation_id) REFERENCES auto_trading_validations(id),
    CONSTRAINT uk_auto_cost_request UNIQUE(user_id, request_key)
);
