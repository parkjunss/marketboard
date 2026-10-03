CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(500) NOT NULL,
    link VARCHAR(255),
    dedup_key VARCHAR(160) NOT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_notifications_user_dedup UNIQUE (user_id, dedup_key),
    INDEX idx_notifications_user_created (user_id, created_at DESC)
);
