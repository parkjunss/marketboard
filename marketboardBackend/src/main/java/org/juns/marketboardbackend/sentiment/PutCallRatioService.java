package org.juns.marketboardbackend.sentiment;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.type.TypeReference;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.PutCallRatioResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-through cache in front of the collector's yfinance-backed put/call volume ratio -- each
 * computation aggregates 8 option-chain fetches (~2s observed, per the collector's sentiment.py).
 * Two different caching strategies share the same table, matched to how each is actually used:
 * <ul>
 *   <li>SPY (the market-wide sentiment card on /market, /overview) is refreshed proactively on a
 *   schedule, same "scheduled refresh + MySQL snapshot" pattern as SectorPerformanceService --
 *   it's requested often enough, by everyone, that it's worth always having fresh.
 *   <li>Every other ticker (individual stock detail pages) is refreshed lazily on request with a
 *   TTL, same read-through-cache pattern as SymbolProfileService/FinancialsService -- proactively
 *   scheduling this for every one of ~500 tracked symbols would mean ~500 * 2s of yfinance calls
 *   per cycle for tickers that might not be looked at all day.
 * </ul>
 */
@Service
public class PutCallRatioService {

    private static final Logger log = LoggerFactory.getLogger(PutCallRatioService.class);
    private static final String SPY_TICKER = "SPY";
    // Shorter than SymbolProfileService/FinancialsService's 24h -- options positioning is more
    // dynamic than a company profile or a quarterly financial statement.
    private static final Duration TICKER_CACHE_TTL = Duration.ofMinutes(30);
    private static final int HISTORY_LIMIT = 30;

    private final CollectorClient collectorClient;
    private final PutCallRatioSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public PutCallRatioService(
            CollectorClient collectorClient, PutCallRatioSnapshotRepository repository, ObjectMapper objectMapper) {
        this.collectorClient = collectorClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${put-call-ratio.cron}")
    @Transactional
    @CacheEvict(value = CacheConfig.PUT_CALL_RATIO, allEntries = true)
    public void refresh() {
        Optional<PutCallRatioResponse> fetched = collectorClient.getPutCallRatio(SPY_TICKER);
        if (fetched.isEmpty()) {
            // Collector/yfinance hiccup -- leave the previous snapshot in place rather than
            // overwrite it with nothing; the cache evict above still clears out to force the next
            // read to hit MySQL, which still has the last good data.
            log.warn("Put/call ratio refresh returned no data -- keeping previous snapshot");
            return;
        }
        upsert(SPY_TICKER, fetched.get());
        log.info("Put/call ratio refreshed ({})", fetched.get().putCallRatio());
    }

    @Transactional(readOnly = true)
    @Cacheable(CacheConfig.PUT_CALL_RATIO)
    public PutCallRatioResponse getLatest() {
        return deserialize(repository
                .findByTickerIgnoreCase(SPY_TICKER)
                .orElseThrow(() -> new ResourceNotFoundException("Put/call ratio not computed yet")));
    }

    @Transactional(readOnly = true)
    public PutCallRatioTrendResponse getLatestTrend() {
        PutCallRatioSnapshot snapshot = repository
                .findByTickerIgnoreCase(SPY_TICKER)
                .orElseThrow(() -> new ResourceNotFoundException("Put/call ratio not computed yet"));
        return PutCallRatioTrendResponse.from(deserialize(snapshot), readHistory(snapshot));
    }

    /** Individual stock detail pages -- see the class-level comment for why this is lazy, not scheduled. */
    @Transactional
    public PutCallRatioResponse getForTicker(String ticker) {
        String normalized = ticker.toUpperCase();
        PutCallRatioSnapshot cached = repository.findByTickerIgnoreCase(normalized).orElse(null);

        if (cached != null && isFresh(cached)) {
            return deserialize(cached);
        }

        Optional<PutCallRatioResponse> fetched = collectorClient.getPutCallRatio(normalized);
        if (fetched.isEmpty()) {
            if (cached != null) {
                // Collector/yfinance hiccup (or rate-limited/slow) -- serve the stale cache rather
                // than fail outright.
                return deserialize(cached);
            }
            throw new ResourceNotFoundException("No options data available for " + normalized);
        }

        upsert(normalized, fetched.get());
        return fetched.get();
    }

    private void upsert(String ticker, PutCallRatioResponse response) {
        String payloadJson = objectMapper.writeValueAsString(response);
        PutCallRatioSnapshot snapshot = repository.findByTickerIgnoreCase(ticker).orElse(null);
        List<PutCallRatioPoint> history = snapshot == null ? new ArrayList<>() : new ArrayList<>(readHistory(snapshot));
        history.add(new PutCallRatioPoint(Instant.now(), response.putCallRatio()));
        history = history.subList(Math.max(0, history.size() - HISTORY_LIMIT), history.size());
        String historyJson = objectMapper.writeValueAsString(history);
        if (snapshot == null) {
            repository.save(PutCallRatioSnapshot.builder()
                    .ticker(ticker)
                    .payloadJson(payloadJson)
                    .ratioHistoryJson(historyJson)
                    .build());
        } else {
            snapshot.update(payloadJson, historyJson);
        }
    }

    private boolean isFresh(PutCallRatioSnapshot snapshot) {
        return snapshot.getComputedAt().isAfter(Instant.now().minus(TICKER_CACHE_TTL));
    }

    private PutCallRatioResponse deserialize(PutCallRatioSnapshot snapshot) {
        return objectMapper.readValue(snapshot.getPayloadJson(), PutCallRatioResponse.class);
    }

    private List<PutCallRatioPoint> readHistory(PutCallRatioSnapshot snapshot) {
        return objectMapper.readValue(snapshot.getRatioHistoryJson(), new TypeReference<>() {});
    }
}
