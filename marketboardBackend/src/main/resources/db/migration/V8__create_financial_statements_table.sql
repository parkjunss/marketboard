CREATE TABLE financial_statements (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker       VARCHAR(10)  NOT NULL,
    payload_json TEXT         NOT NULL,
    fetched_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uk_financial_statements_ticker UNIQUE (ticker)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
