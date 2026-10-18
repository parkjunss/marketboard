CREATE TABLE saved_portfolio_designs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    selected_id VARCHAR(40) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    CONSTRAINT fk_saved_design_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_saved_design_user_id (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
