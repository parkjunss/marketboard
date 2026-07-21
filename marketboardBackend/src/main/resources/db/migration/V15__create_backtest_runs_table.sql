CREATE TABLE backtest_runs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    name          VARCHAR(100) NOT NULL,
    config_json   TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    result_json   TEXT         NULL,
    error_message VARCHAR(500) NULL,
    created_at    TIMESTAMP    NOT NULL,
    completed_at  TIMESTAMP    NULL,
    CONSTRAINT fk_backtest_runs_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_backtest_runs_user ON backtest_runs (user_id);
