ALTER TABLE stock_screening_snapshots
    ADD COLUMN ema_20 DECIMAL(18,4) NULL AFTER above_sma_200,
    ADD COLUMN ema_60 DECIMAL(18,4) NULL AFTER ema_20,
    ADD COLUMN macd_line DECIMAL(18,4) NULL AFTER ema_60,
    ADD COLUMN macd_signal DECIMAL(18,4) NULL AFTER macd_line,
    ADD COLUMN macd_histogram DECIMAL(18,4) NULL AFTER macd_signal,
    ADD COLUMN bollinger_percent_b_20 DECIMAL(12,4) NULL AFTER macd_histogram,
    ADD COLUMN atr_pct_14 DECIMAL(12,4) NULL AFTER bollinger_percent_b_20,
    ADD COLUMN relative_volume_20 DECIMAL(12,4) NULL AFTER atr_pct_14;
