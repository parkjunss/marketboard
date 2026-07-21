package org.juns.marketboardbackend.backtest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.juns.marketboardbackend.collector.BacktestEngineResult;

public record BacktestRunResponse(
        Long id,
        String name,
        String status,
        List<String> tickers,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal initialCapital,
        BigDecimal riskFreeRate,
        BacktestEngineResult result,
        String errorMessage,
        Instant createdAt,
        Instant completedAt) {
}
