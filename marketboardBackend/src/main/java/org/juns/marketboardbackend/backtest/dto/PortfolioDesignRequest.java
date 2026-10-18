package org.juns.marketboardbackend.backtest.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

public record PortfolioDesignRequest(
        @NotNull @Size(min=2, max=10) List<@NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9.\\-]{0,14}") String> tickers,
        @NotNull LocalDate startDate, @NotNull LocalDate endDate,
        @NotNull @DecimalMin("1") @DecimalMax("1000000000") BigDecimal capital,
        @NotNull @DecimalMin("0") @DecimalMax("1000000000") BigDecimal cash,
        @NotNull @DecimalMin(value="0", inclusive=false) @DecimalMax("100") BigDecimal maxWeightPct,
        @NotNull @DecimalMin(value="0", inclusive=false) @DecimalMax("200") BigDecimal riskLimitPct,
        LocalDate validationStartDate, LocalDate validationEndDate) {
    public PortfolioDesignRequest {
        if (tickers != null) tickers = tickers.stream().map(t -> t == null ? null : t.trim().toUpperCase(Locale.ROOT)).distinct().toList();
    }
    public void validateConditions() {
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        if (startDate.getYear() < 1991 || days < 60 || days > 3660
                || !endDate.isBefore(LocalDate.now(ZoneId.of("America/New_York"))))
            throw new IllegalArgumentException("분석 기간은 1991년 이후 60일~10년이며 종료일은 뉴욕 오늘 이전이어야 합니다.");
        if (cash.compareTo(capital) > 0) throw new IllegalArgumentException("남길 현금이 투자금보다 큽니다.");
        if ((validationStartDate == null) != (validationEndDate == null))
            throw new IllegalArgumentException("검증 시작일과 종료일을 함께 입력하세요.");
        if (validationStartDate != null && (!validationStartDate.isAfter(endDate)
                || !validationEndDate.isAfter(validationStartDate)
                || !validationEndDate.isBefore(LocalDate.now(ZoneId.of("America/New_York")))
                || ChronoUnit.DAYS.between(validationStartDate, validationEndDate) > 3660))
            throw new IllegalArgumentException("검증 기간은 산출 종료일 이후, 뉴욕 오늘 이전의 10년 이내 기간이어야 합니다.");
    }
}
