package org.juns.marketboardbackend.collector;

import java.math.BigDecimal;

public record MomentumScreenerCandidate(
        String ticker,
        BigDecimal momentumPct,
        BigDecimal volatilityPct,
        boolean trendUp,
        BigDecimal rsi14,
        BigDecimal revenueGrowthPct,
        BigDecimal returnOnEquityPct,
        BigDecimal profitMarginPct,
        BigDecimal trailingPE,
        BigDecimal marketCap,
        BigDecimal totalRevenue,
        BigDecimal newsSentiment,
        Integer newsCount) {
}
