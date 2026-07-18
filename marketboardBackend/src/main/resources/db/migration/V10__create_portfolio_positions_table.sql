CREATE TABLE portfolio_positions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    portfolio_id BIGINT        NOT NULL,
    symbol_id    BIGINT        NOT NULL,
    quantity     DECIMAL(18,6) NOT NULL,
    avg_cost     DECIMAL(18,4) NOT NULL,
    updated_at   TIMESTAMP     NOT NULL,
    CONSTRAINT fk_portfolio_positions_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id),
    CONSTRAINT fk_portfolio_positions_symbol FOREIGN KEY (symbol_id) REFERENCES symbols (id),
    CONSTRAINT uk_portfolio_positions_portfolio_symbol UNIQUE (portfolio_id, symbol_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
