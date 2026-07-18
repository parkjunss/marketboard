CREATE TABLE dashboard_configs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    layout_key  VARCHAR(30)  NOT NULL,
    panels_json TEXT         NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT fk_dashboard_configs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_dashboard_configs_user UNIQUE (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
