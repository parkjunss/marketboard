package org.juns.marketboardbackend.backtest.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public record EtfComparisonRequest(
        @NotNull LocalDate startDate, @NotNull LocalDate endDate,
        @NotNull @DecimalMin("1") @DecimalMax("1000000000") BigDecimal initialCapital,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal feeBps,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal slippageBps) {
    public void validatePeriod() {
        if (startDate.getYear() < 1991 || !startDate.isBefore(endDate)
                || ChronoUnit.DAYS.between(startDate, endDate) > 3660) {
            throw new IllegalArgumentException("기간은 1991년 이후, 시작일 < 종료일, 최대 10년이어야 합니다.");
        }
        if (!endDate.isBefore(LocalDate.now(ZoneId.of("America/New_York")))) {
            throw new IllegalArgumentException("종료일은 뉴욕 기준 오늘 이전이어야 합니다.");
        }
    }
}
