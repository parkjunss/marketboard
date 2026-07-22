package org.juns.marketboardbackend.sentiment;

import java.util.Optional;
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
 * Read-through cache in front of the collector's yfinance-backed SPY put/call volume ratio, same
 * "scheduled refresh + MySQL snapshot" pattern as SectorPerformanceService/MarketBreadthService --
 * each computation aggregates 8 option-chain fetches from yfinance (~2s observed, per the
 * collector's sentiment.py), so a request landing on a cache miss paid that full latency. Only SPY
 * is persisted: it's the only ticker the frontend (or anything else in this app) ever actually
 * requests -- see MarketSentimentController.
 */
@Service
public class PutCallRatioService {

    private static final Logger log = LoggerFactory.getLogger(PutCallRatioService.class);
    private static final String TICKER = "SPY";

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
        Optional<PutCallRatioResponse> fetched = collectorClient.getPutCallRatio(TICKER);
        if (fetched.isEmpty()) {
            // Collector/yfinance hiccup -- leave the previous snapshot in place rather than
            // overwrite it with nothing; the cache evict above still clears out to force the next
            // read to hit MySQL, which still has the last good data.
            log.warn("Put/call ratio refresh returned no data -- keeping previous snapshot");
            return;
        }

        String payloadJson = objectMapper.writeValueAsString(fetched.get());
        PutCallRatioSnapshot snapshot = repository.findTopByOrderByIdDesc().orElse(null);
        if (snapshot == null) {
            repository.save(PutCallRatioSnapshot.builder().payloadJson(payloadJson).build());
        } else {
            snapshot.update(payloadJson);
        }
        log.info("Put/call ratio refreshed ({})", fetched.get().putCallRatio());
    }

    @Transactional(readOnly = true)
    @Cacheable(CacheConfig.PUT_CALL_RATIO)
    public PutCallRatioResponse getLatest() {
        PutCallRatioSnapshot snapshot = repository
                .findTopByOrderByIdDesc()
                .orElseThrow(() -> new ResourceNotFoundException("Put/call ratio not computed yet"));
        return objectMapper.readValue(snapshot.getPayloadJson(), PutCallRatioResponse.class);
    }
}
