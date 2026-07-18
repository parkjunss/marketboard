package org.juns.marketboardbackend.alert.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.juns.marketboardbackend.alert.AlertCondition;

public record AlertRequest(
        @NotBlank String ticker,
        @NotNull AlertCondition condition,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal targetPrice) {
}
