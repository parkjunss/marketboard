package org.juns.marketboardbackend.collector;

import java.util.List;
import java.util.Map;

/**
 * 20-day volatility risk state per ETF from the collector's risk_state.py. A reference for
 * judgment, not an order signal -- see docs/Risk_Signal_Results.md for the evidence and limits.
 */
public record MarketRiskState(String expectedTradingDate, String fetchedAt, String source, String adjustment,
        Thresholds thresholds, Map<String, Object> evidence, Map<String, Integer> counts, List<Asset> assets) {

    public record Thresholds(int caution, int risk, boolean validated) {}

    public record Asset(String ticker, String name, String group, String status, String state, Double volatility20Pct,
            Double percentile, Double medianVolatilityPct, Double drawdown252Pct, Double trend200Pct,
            String historyStart, String tradingDate) {}
}
