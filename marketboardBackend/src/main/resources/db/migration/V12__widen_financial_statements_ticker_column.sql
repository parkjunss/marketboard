-- ticker was VARCHAR(10), inconsistent with symbols.ticker's VARCHAR(20). A ticker longer than
-- 10 chars (e.g. a garbage/typo'd lookup) failed the insert with a data-truncation error that
-- surfaced to callers as a confusing 403 instead of the intended 404 "unknown ticker" response.
ALTER TABLE financial_statements
    MODIFY COLUMN ticker VARCHAR(20) NOT NULL;
