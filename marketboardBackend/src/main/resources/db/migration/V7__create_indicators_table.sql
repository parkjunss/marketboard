CREATE TABLE indicators (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol_id      BIGINT        NOT NULL,
    indicator_type VARCHAR(20)   NOT NULL,
    timeframe      VARCHAR(10)   NOT NULL,
    value          DECIMAL(18,4) NOT NULL,
    computed_at    TIMESTAMP     NOT NULL,
    CONSTRAINT fk_indicators_symbol FOREIGN KEY (symbol_id) REFERENCES symbols (id),
    CONSTRAINT uk_indicators_symbol_type_timeframe UNIQUE (symbol_id, indicator_type, timeframe)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
