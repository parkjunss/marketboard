CREATE TABLE chart_indicator_settings (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    settings_json TEXT         NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT fk_chart_indicator_settings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_chart_indicator_settings_user UNIQUE (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
