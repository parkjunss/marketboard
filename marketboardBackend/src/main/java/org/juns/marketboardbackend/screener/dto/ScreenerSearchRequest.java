package org.juns.marketboardbackend.screener.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

public record ScreenerSearchRequest(
        MomentumPeriod momentumPeriod,
        BigDecimal minMomentumPct,
        BigDecimal maxRsi,
        Boolean aboveSma200,
        BigDecimal minMarketCap,
        BigDecimal minRevenue,
        BigDecimal minRevenueGrowth,
        BigDecimal minRoe,
        BigDecimal maxTrailingPe,
        BigDecimal minNewsSentiment,
        ScreenerSort sort,
        @Min(0) int page,
        @Min(1) @Max(100) int size) {
    public ScreenerSearchRequest {
        if (momentumPeriod == null) momentumPeriod = MomentumPeriod.SIX_MONTHS;
        if (sort == null) sort = new ScreenerSort(null, null);
        if (size == 0) size = 20;
    }
}
