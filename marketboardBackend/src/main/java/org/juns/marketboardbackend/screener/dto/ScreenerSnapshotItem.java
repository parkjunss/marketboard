package org.juns.marketboardbackend.screener.dto;

import java.math.BigDecimal;

public record ScreenerSnapshotItem(
        String ticker, BigDecimal price,
        BigDecimal momentum3m, BigDecimal momentum6m, BigDecimal momentum12m,
        BigDecimal volatility20d, BigDecimal rsi14,
        BigDecimal sma50, BigDecimal sma100, BigDecimal sma200, Boolean aboveSma200,
        BigDecimal ema20, BigDecimal ema60,
        BigDecimal macdLine, BigDecimal macdSignal, BigDecimal macdHistogram,
        BigDecimal bollingerPercentB20, BigDecimal atrPct14, BigDecimal relativeVolume20,
        BigDecimal marketCap, BigDecimal revenueTtm, BigDecimal revenueGrowth,
        BigDecimal roe, BigDecimal profitMargin, BigDecimal trailingPe,
        BigDecimal newsSentiment, Integer newsCount) {}
