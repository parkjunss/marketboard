CREATE TABLE portfolio_weight_rules (
    portfolio_id BIGINT NOT NULL,
    max_position_weight DECIMAL(7,6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (portfolio_id),
    CONSTRAINT fk_portfolio_weight_rules_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES portfolios(id) ON DELETE CASCADE,
    CONSTRAINT chk_portfolio_weight_rules_max CHECK (max_position_weight > 0 AND max_position_weight <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE portfolio_investment_theses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    portfolio_id BIGINT NOT NULL,
    symbol_id BIGINT NOT NULL,
    revision INT NOT NULL,
    thesis VARCHAR(2000) NOT NULL,
    invalidation_condition VARCHAR(2000) NOT NULL,
    target_weight DECIMAL(7,6) NOT NULL,
    max_weight DECIMAL(7,6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_theses_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES portfolios(id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_theses_symbol FOREIGN KEY (symbol_id)
        REFERENCES symbols(id),
    CONSTRAINT uk_portfolio_theses_revision UNIQUE (portfolio_id, symbol_id, revision),
    CONSTRAINT chk_portfolio_theses_revision CHECK (revision > 0),
    CONSTRAINT chk_portfolio_theses_target CHECK (target_weight >= 0 AND target_weight <= 1),
    CONSTRAINT chk_portfolio_theses_max CHECK (max_weight > 0 AND max_weight <= 1),
    CONSTRAINT chk_portfolio_theses_weights CHECK (target_weight <= max_weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
