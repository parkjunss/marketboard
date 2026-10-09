package org.juns.marketboardbackend.portfolio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import org.juns.marketboardbackend.portfolio.PortfolioTransactionType;

public record PortfolioTransactionRequest(
        @NotBlank String ticker,
        @NotNull PortfolioTransactionType type,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal unitPrice,
        @NotNull @DecimalMin("0") BigDecimal fee,
        @NotNull Instant occurredAt) {}
