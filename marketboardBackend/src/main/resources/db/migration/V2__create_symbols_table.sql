CREATE TABLE symbols (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker     VARCHAR(20)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    exchange   VARCHAR(20)  NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    priority   INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_symbols_ticker UNIQUE (ticker)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
