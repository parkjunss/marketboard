package org.juns.marketboardbackend.collector;

import java.math.BigDecimal;

/** Any null field means "use the collector's default for that condition" -- see app/screener.py. */
public record MomentumScreenerRequest(
        int topN,
        Integer momentumWindowDays,
        Integer trendMaWindow,
        BigDecimal correlationThreshold,
        BigDecimal minMomentumPct,
        BigDecimal maxRsi,
        BigDecimal minMarketCap,
        BigDecimal minRevenue) {
}
