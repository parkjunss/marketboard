package org.juns.marketboardbackend.alert.dto;

import java.math.BigDecimal;
import org.juns.marketboardbackend.alert.AlertCondition;

public record AlertNotification(
        Long alertId, String ticker, AlertCondition condition, BigDecimal targetPrice, BigDecimal price) {
}
