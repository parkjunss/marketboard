CREATE TABLE market_index_history_snapshot (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    slug         VARCHAR(20)  NOT NULL,
    payload_json TEXT         NOT NULL,
    computed_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uk_market_index_history_slug UNIQUE (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
