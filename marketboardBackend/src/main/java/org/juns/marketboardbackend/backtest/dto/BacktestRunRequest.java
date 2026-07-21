package org.juns.marketboardbackend.backtest.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BacktestRunRequest(
        @NotBlank @Size(max = 100) String name,
        @NotEmpty @Size(max = 10) List<@NotBlank String> tickers,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Positive BigDecimal initialCapital,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal riskFreeRate) {
}
