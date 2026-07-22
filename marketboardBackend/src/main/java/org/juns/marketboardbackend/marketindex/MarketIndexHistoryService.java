package org.juns.marketboardbackend.marketindex;

import java.util.List;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.MarketIndexCandle;
import org.juns.marketboardbackend.collector.MarketIndexInfo;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-through cache in front of the collector's yfinance-backed daily OHLC history for each
 * macro index (S&amp;P 500, NASDAQ, VIX, Treasury yields, ...), same "scheduled refresh + MySQL
 * snapshot" pattern as SectorPerformanceService: these are daily bars, so a request landing on a
 * live yfinance fetch (one per index, ~9 of them) on every cache miss was slow for no reason --
 * the data can't have changed since the last close anyway.
 */
@Service
public class MarketIndexHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MarketIndexHistoryService.class);

    private final CollectorClient collectorClient;
    private final MarketIndexHistorySnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public MarketIndexHistoryService(
            CollectorClient collectorClient, MarketIndexHistorySnapshotRepository repository, ObjectMapper objectMapper) {
        this.collectorClient = collectorClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${market-index-history.cron}")
    @Transactional
    @CacheEvict(value = CacheConfig.MARKET_INDEX_HISTORY, allEntries = true)
    public void refresh() {
        // getMarketIndices() is just the static slug/name list (no I/O on the collector side), so
        // this doubles as service discovery for which slugs to refresh rather than hardcoding the
        // list a second time here.
        List<MarketIndexInfo> indices = collectorClient.getMarketIndices();
        int refreshed = 0;
        for (MarketIndexInfo index : indices) {
            List<MarketIndexCandle> candles = collectorClient.getMarketIndexHistory(index.slug());
            if (candles.isEmpty()) {
                // Collector/yfinance hiccup for this one index -- leave its previous snapshot in
                // place rather than overwrite it with nothing.
                log.warn("Market index history refresh returned no data for {} -- keeping previous snapshot", index.slug());
                continue;
            }
            String payloadJson = objectMapper.writeValueAsString(candles);
            MarketIndexHistorySnapshot snapshot = repository.findBySlug(index.slug()).orElse(null);
            if (snapshot == null) {
                repository.save(MarketIndexHistorySnapshot.builder().slug(index.slug()).payloadJson(payloadJson).build());
            } else {
                snapshot.update(payloadJson);
            }
            refreshed++;
        }
        log.info("Market index history refreshed ({}/{} indices)", refreshed, indices.size());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.MARKET_INDEX_HISTORY, key = "#slug")
    public List<MarketIndexCandle> getHistory(String slug) {
        MarketIndexHistorySnapshot snapshot = repository
                .findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("No history computed yet for index " + slug));
        return objectMapper.readValue(snapshot.getPayloadJson(), new TypeReference<List<MarketIndexCandle>>() {});
    }
}
