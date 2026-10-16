package org.juns.marketboardbackend.collector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EtfComparisonResult(boolean ready, String message,
        LocalDate requestedStartDate, LocalDate requestedEndDate, LocalDate startDate, LocalDate endDate,
        BigDecimal initialCapital, BigDecimal feeBps, BigDecimal slippageBps, BigDecimal cashRatePct,
        String currency, List<Strategy> strategies) {
    public record Strategy(String strategyId, String label, BacktestEngineResult.BacktestMetrics metrics,
            List<Point> equityCurve, BigDecimal totalFees, BigDecimal totalSlippage, BigDecimal turnoverPct,
            List<Trade> trades, List<Rebalance> rebalances, List<Regime> regimes) {}
    public record Point(LocalDate date, BigDecimal value, BigDecimal returnPct) {}
    public record Trade(String ticker, String side, BigDecimal quantity, BigDecimal price, BigDecimal notional,
            BigDecimal fee, BigDecimal slippage, LocalDate signalDate, LocalDate executionDate) {}
    public record Rebalance(LocalDate signalDate, LocalDate executionDate, BigDecimal cashWeight, List<Target> targets) {}
    public record Target(String ticker, BigDecimal weight) {}
    public record Regime(String label, int days, BigDecimal returnPct) {}
}
