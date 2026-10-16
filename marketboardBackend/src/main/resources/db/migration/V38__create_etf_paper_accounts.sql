CREATE TABLE etf_paper_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    state_json MEDIUMTEXT NOT NULL,
    last_error VARCHAR(500),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (review_id) REFERENCES investment_reviews(id) ON DELETE CASCADE,
    INDEX idx_etf_paper_owner (user_id, id)
);
CREATE TABLE etf_paper_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    snapshot_date DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    FOREIGN KEY (account_id) REFERENCES etf_paper_accounts(id) ON DELETE CASCADE,
    UNIQUE KEY uk_etf_paper_snapshot (account_id, kind, snapshot_date)
);
