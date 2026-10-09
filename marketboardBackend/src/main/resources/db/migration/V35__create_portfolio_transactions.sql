CREATE TABLE portfolio_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    portfolio_id BIGINT NOT NULL,
    symbol_id BIGINT NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    quantity DECIMAL(18,6) NULL,
    unit_price DECIMAL(18,4) NULL,
    fee DECIMAL(18,4) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    occurred_at TIMESTAMP(6) NOT NULL,
    source_transaction_key VARCHAR(128) NULL,
    source_position_id BIGINT NULL,
    reversal_of_transaction_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_transactions_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES portfolios(id),
    CONSTRAINT fk_portfolio_transactions_symbol
        FOREIGN KEY (symbol_id) REFERENCES symbols(id),
    CONSTRAINT fk_portfolio_transactions_reversal
        FOREIGN KEY (reversal_of_transaction_id) REFERENCES portfolio_transactions(id),
    CONSTRAINT uk_portfolio_transactions_source
        UNIQUE (portfolio_id, source_transaction_key),
    CONSTRAINT uk_portfolio_transactions_opening
        UNIQUE (portfolio_id, source_position_id),
    CONSTRAINT uk_portfolio_transactions_reversal
        UNIQUE (reversal_of_transaction_id),
    CONSTRAINT chk_portfolio_transactions_type
        CHECK (transaction_type IN ('OPENING_BALANCE', 'BUY', 'SELL', 'REVERSAL')),
    CONSTRAINT chk_portfolio_transactions_currency
        CHECK (currency = 'USD'),
    CONSTRAINT chk_portfolio_transactions_fee
        CHECK (fee >= 0),
    CONSTRAINT chk_portfolio_transactions_shape CHECK (
        (transaction_type = 'OPENING_BALANCE'
            AND quantity > 0 AND unit_price > 0 AND fee = 0
            AND source_position_id IS NOT NULL AND reversal_of_transaction_id IS NULL)
        OR
        (transaction_type IN ('BUY', 'SELL')
            AND quantity > 0 AND unit_price > 0
            AND source_position_id IS NULL AND reversal_of_transaction_id IS NULL)
        OR
        (transaction_type = 'REVERSAL'
            AND quantity IS NULL AND unit_price IS NULL AND fee = 0
            AND source_position_id IS NULL AND reversal_of_transaction_id IS NOT NULL)
    ),
    KEY idx_portfolio_transactions_time (portfolio_id, occurred_at, id),
    KEY idx_portfolio_transactions_symbol (portfolio_id, symbol_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
