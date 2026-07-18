package org.juns.marketboardbackend.collector;

import java.util.List;

public record FinancialsResponse(
        String ticker,
        String name,
        List<Integer> years,
        List<EarningsYear> earningsAnalysis,
        List<GrowthYear> growthAnalysis,
        List<ProfitabilityYear> profitabilityAnalysis,
        List<CashFlowYear> cashFlowAnalysis,
        List<MarginsYear> marginsAnalysis,
        List<MarketYear> marketAnalysis,
        Kpis kpis) {

    public record EarningsYear(int year, Double revenue, Double grossProfit, Double operatingIncome, Double netIncome) {
    }

    public record GrowthYear(int year, Double revenueGrowthPct, Double operatingIncomeGrowthPct) {
    }

    public record ProfitabilityYear(int year, Double roePct, Double roaPct) {
    }

    public record CashFlowYear(int year, Double operatingCashFlow, Double freeCashFlow) {
    }

    public record MarginsYear(int year, Double grossMarginPct, Double operatingMarginPct, Double netMarginPct) {
    }

    public record MarketYear(int year, Double peRatio, Double pbRatio, Double psRatio) {
    }

    public record Kpis(
            Double quickRatio,
            Double currentRatio,
            Double debtToCapitalPct,
            Double totalDebtToFcf,
            Double cashAndEquivalents,
            Double interestCoverage) {
    }
}
