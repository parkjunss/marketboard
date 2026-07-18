CREATE TABLE price_history (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol_id  BIGINT        NOT NULL,
    ts         TIMESTAMP     NOT NULL,
    open       DECIMAL(18,4) NOT NULL,
    high       DECIMAL(18,4) NOT NULL,
    low        DECIMAL(18,4) NOT NULL,
    close      DECIMAL(18,4) NOT NULL,
    volume     BIGINT        NOT NULL,
    timeframe  VARCHAR(10)   NOT NULL,
    CONSTRAINT fk_price_history_symbol FOREIGN KEY (symbol_id) REFERENCES symbols (id),
    CONSTRAINT uk_price_history_symbol_tf_ts UNIQUE (symbol_id, timeframe, ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
