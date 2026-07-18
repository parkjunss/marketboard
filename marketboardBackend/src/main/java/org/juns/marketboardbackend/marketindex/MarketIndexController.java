package org.juns.marketboardbackend.marketindex;

import java.util.List;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.MarketIndexCandle;
import org.juns.marketboardbackend.collector.MarketIndexInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Broad market indices (S&P 500, NASDAQ, VIX, Treasury yields, ...) — daily-only via yfinance,
 * unlike tracked stocks these aren't on Finnhub's realtime feed so there's no WS/DB path here.
 */
@RestController
@RequestMapping("/api/market-indices")
public class MarketIndexController {

    private final CollectorClient collectorClient;

    public MarketIndexController(CollectorClient collectorClient) {
        this.collectorClient = collectorClient;
    }

    @GetMapping
    public List<MarketIndexInfo> getIndices() {
        return collectorClient.getMarketIndices();
    }

    @GetMapping("/{slug}/history")
    public List<MarketIndexCandle> getHistory(@PathVariable String slug) {
        return collectorClient.getMarketIndexHistory(slug);
    }
}
