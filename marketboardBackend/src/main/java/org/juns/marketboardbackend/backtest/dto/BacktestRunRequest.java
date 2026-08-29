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
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal riskFreeRate,
        // "BUY_AND_HOLD" (default when blank) | "SMA_CROSSOVER" | "PERIODIC_REBALANCE" |
        // "VOLATILITY_TARGET" -- validated against the actual param requirements by the
        // collector's engine, not here, since which fields are required depends on which type this is.
        String strategyType,
        Integer smaShortWindow,
        Integer smaLongWindow,
        String rebalanceFrequency,
        BigDecimal targetVolatilityPct,
        BigDecimal vixThreshold) {
}
