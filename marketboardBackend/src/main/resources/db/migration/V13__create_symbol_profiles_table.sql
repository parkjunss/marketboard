CREATE TABLE symbol_profiles (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker       VARCHAR(20)  NOT NULL,
    payload_json TEXT         NOT NULL,
    fetched_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uk_symbol_profiles_ticker UNIQUE (ticker)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
