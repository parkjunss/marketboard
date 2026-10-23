CREATE TABLE market_reports (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    market VARCHAR(16) NOT NULL,
    trading_date DATE NOT NULL,
    report_version INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    published_at TIMESTAMP(6) NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    CONSTRAINT uk_market_report_session UNIQUE (market, trading_date, report_version)
);
