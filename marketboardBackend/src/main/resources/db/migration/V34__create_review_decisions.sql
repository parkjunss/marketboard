CREATE TABLE review_decisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    choice VARCHAR(20) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    follow_up_date DATE NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_review_decisions_review FOREIGN KEY (review_id) REFERENCES investment_reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_decisions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_review_decisions_review_created (review_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
