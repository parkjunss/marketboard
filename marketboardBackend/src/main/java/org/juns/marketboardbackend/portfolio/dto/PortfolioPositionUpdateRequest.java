package org.juns.marketboardbackend.portfolio.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PortfolioPositionUpdateRequest(
        @NotNull @DecimalMin(value = "0.000001") BigDecimal quantity, @NotNull @DecimalMin(value = "0") BigDecimal avgCost,
        @NotNull @jakarta.validation.constraints.Min(0) Long version) {
}
