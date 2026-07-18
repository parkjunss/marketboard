CREATE TABLE alerts (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    symbol_id    BIGINT        NOT NULL,
    direction    VARCHAR(10)   NOT NULL,
    target_price DECIMAL(18,4) NOT NULL,
    triggered_at TIMESTAMP     NULL,
    created_at   TIMESTAMP     NOT NULL,
    CONSTRAINT fk_alerts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_alerts_symbol FOREIGN KEY (symbol_id) REFERENCES symbols (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
