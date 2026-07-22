CREATE TABLE options_levels_snapshot (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker       VARCHAR(20)  NOT NULL,
    payload_json TEXT         NOT NULL,
    computed_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uk_options_levels_snapshot_ticker UNIQUE (ticker)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
