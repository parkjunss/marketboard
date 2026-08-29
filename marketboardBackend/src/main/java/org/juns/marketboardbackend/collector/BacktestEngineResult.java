package org.juns.marketboardbackend.collector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BacktestEngineResult(
        List<EquityPoint> equityCurve,
        BacktestMetrics metrics,
        List<TickerStat> tickerStats,
        BenchmarkStats benchmarkStats) {

    public record EquityPoint(LocalDate date, BigDecimal portfolioValue, BigDecimal benchmarkValue) {
    }

    public record BacktestMetrics(
            BigDecimal totalReturnPct,
            BigDecimal cagrPct,
            BigDecimal maxDrawdownPct,
            BigDecimal volatilityPct,
            BigDecimal sharpeRatio) {
    }

    public record TickerStat(String ticker, BigDecimal returnPct, BigDecimal volatilityPct) {
    }

    public record BenchmarkStats(String ticker, BigDecimal returnPct, BigDecimal volatilityPct) {
    }
}
