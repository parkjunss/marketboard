CREATE TABLE news_snapshot (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    payload_json TEXT      NOT NULL,
    computed_at  TIMESTAMP NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
