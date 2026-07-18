CREATE TABLE watchlist_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    symbol_id  BIGINT NOT NULL,
    sort_order INT    NOT NULL DEFAULT 0,
    CONSTRAINT fk_watchlist_items_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_watchlist_items_symbol FOREIGN KEY (symbol_id) REFERENCES symbols (id),
    CONSTRAINT uk_watchlist_items_user_symbol UNIQUE (user_id, symbol_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
