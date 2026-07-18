package org.juns.marketboardbackend.indicator.dto;

import java.math.BigDecimal;
import java.time.Instant;
import org.juns.marketboardbackend.indicator.Indicator;
import org.juns.marketboardbackend.indicator.IndicatorType;

public record IndicatorResponse(IndicatorType type, String timeframe, BigDecimal value, Instant computedAt) {

    public static IndicatorResponse from(Indicator indicator) {
        return new IndicatorResponse(
                indicator.getIndicatorType(), indicator.getTimeframe(), indicator.getValue(), indicator.getComputedAt());
    }
}
