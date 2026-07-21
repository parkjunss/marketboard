package org.juns.marketboardbackend.collector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BacktestEngineResult(List<EquityPoint> equityCurve, BacktestMetrics metrics) {

    public record EquityPoint(LocalDate date, BigDecimal portfolioValue, BigDecimal benchmarkValue) {
    }

    public record BacktestMetrics(
            BigDecimal totalReturnPct,
            BigDecimal cagrPct,
            BigDecimal maxDrawdownPct,
            BigDecimal volatilityPct,
            BigDecimal sharpeRatio) {
    }
}
