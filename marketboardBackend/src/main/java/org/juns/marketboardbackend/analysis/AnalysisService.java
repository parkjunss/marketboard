package org.juns.marketboardbackend.analysis;

import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.StockAnalysisResult;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Single-stock quantitative analysis (volatility, VaR/CVaR, Hurst exponent, drawdown,
 * beta/correlation vs SPY, Monte Carlo price projection) -- always a live run against the
 * collector, no DB persistence. Same reasoning as ScreenerService: this is "today's read on this
 * ticker", not a config/history a user would revisit.
 */
@Service
public class AnalysisService {

    private final CollectorClient collectorClient;

    public AnalysisService(CollectorClient collectorClient) {
        this.collectorClient = collectorClient;
    }

    public StockAnalysisResult analyze(String ticker, Integer lookbackDays, Integer monteCarloHorizonDays, Integer monteCarloPaths) {
        return collectorClient
                .getStockAnalysis(ticker.toUpperCase(), lookbackDays, monteCarloHorizonDays, monteCarloPaths)
                .orElseThrow(() -> new ResourceNotFoundException("정량 분석 결과를 가져오지 못했습니다: " + ticker));
    }
}
