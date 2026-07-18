package org.juns.marketboardbackend.alert.dto;

import java.math.BigDecimal;
import java.time.Instant;
import org.juns.marketboardbackend.alert.Alert;
import org.juns.marketboardbackend.alert.AlertCondition;

public record AlertResponse(
        Long id,
        String ticker,
        AlertCondition condition,
        BigDecimal targetPrice,
        Instant triggeredAt,
        Instant createdAt) {

    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getSymbol().getTicker(),
                alert.getCondition(),
                alert.getTargetPrice(),
                alert.getTriggeredAt(),
                alert.getCreatedAt());
    }
}
