package org.juns.marketboardbackend.collector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record StockAnalysisResult(
        String ticker,
        LocalDate asOfDate,
        BigDecimal lastPrice,
        int lookbackDays,
        Volatility volatility,
        // Keyed by confidence level as a string ("95", "99") -- matches app/quant_analysis.py's
        // VAR_CONFIDENCE_LEVELS, deliberately a map rather than fixed fields so new confidence
        // levels can be added on the Python side without a matching Java change.
        Map<String, RiskLevel> risk,
        Distribution distribution,
        BigDecimal hurstExponent,
        String hurstInterpretation,
        Drawdown drawdown,
        Benchmark benchmark,
        MonteCarlo monteCarlo) {

    public record Volatility(BigDecimal annualizedPct) {
    }

    public record RiskLevel(BigDecimal varPct, BigDecimal cvarPct) {
    }

    public record Distribution(BigDecimal skewness, BigDecimal excessKurtosis) {
    }

    public record Drawdown(BigDecimal maxDrawdownPct, Integer maxDrawdownDurationDays) {
    }

    public record Benchmark(String ticker, BigDecimal beta, BigDecimal correlation) {
    }

    public record MonteCarlo(int horizonDays, int paths, Map<String, List<BigDecimal>> percentiles) {
    }
}
