CREATE TABLE stock_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    CONSTRAINT fk_stock_report_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_stock_report_user_ticker (user_id, ticker, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
