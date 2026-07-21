CREATE TABLE market_breadth_snapshots (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_date      DATE      NOT NULL,
    advancing_count    INT       NOT NULL,
    declining_count    INT       NOT NULL,
    unchanged_count    INT       NOT NULL,
    new_52w_high_count INT       NOT NULL,
    new_52w_low_count  INT       NOT NULL,
    universe_size      INT       NOT NULL,
    computed_at        TIMESTAMP NOT NULL,
    CONSTRAINT uk_market_breadth_snapshots_date UNIQUE (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
