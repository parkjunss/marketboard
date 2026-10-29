package org.juns.marketboardbackend.marketindex;

import java.util.List;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.MarketIndexCandle;
import org.juns.marketboardbackend.collector.MarketIndexInfo;
import org.juns.marketboardbackend.collector.MarketRiskState;
import org.juns.marketboardbackend.collector.SectorPerformance;
import org.juns.marketboardbackend.sectorperformance.SectorPerformanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Broad market indices (S&P 500, NASDAQ, VIX, Treasury yields, ...) — daily-only via yfinance,
 * unlike tracked stocks these aren't on Finnhub's realtime feed. The index list itself is static
 * (no I/O on the collector side); history and sector performance are DB-backed on a schedule --
 * see {@link MarketIndexHistoryService}, {@link SectorPerformanceService}.
 */
@RestController
@RequestMapping("/api/market-indices")
public class MarketIndexController {

    private final CollectorClient collectorClient;
    private final MarketIndexHistoryService marketIndexHistoryService;
    private final SectorPerformanceService sectorPerformanceService;
    private final MarketRiskStateService marketRiskStateService;

    public MarketIndexController(
            CollectorClient collectorClient,
            MarketIndexHistoryService marketIndexHistoryService,
            SectorPerformanceService sectorPerformanceService,
            MarketRiskStateService marketRiskStateService) {
        this.collectorClient = collectorClient;
        this.marketIndexHistoryService = marketIndexHistoryService;
        this.sectorPerformanceService = sectorPerformanceService;
        this.marketRiskStateService = marketRiskStateService;
    }

    @GetMapping
    public List<MarketIndexInfo> getIndices() {
        return collectorClient.getMarketIndices();
    }

    @GetMapping("/{slug}/history")
    public List<MarketIndexCandle> getHistory(@PathVariable String slug) {
        return marketIndexHistoryService.getHistory(slug);
    }

    @GetMapping("/sectors/performance")
    public List<SectorPerformance> getSectorPerformance() {
        return sectorPerformanceService.getLatest();
    }

    @GetMapping("/risk-state")
    public MarketRiskState getRiskState() {
        return marketRiskStateService.getLatest();
    }
}
