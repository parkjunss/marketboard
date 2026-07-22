package org.juns.marketboardbackend.sectorperformance;

import java.util.List;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.SectorPerformance;
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
 * Read-through cache in front of the collector's yfinance-backed sector-rotation ranking, same
 * reasoning as SymbolProfileService/FinancialsService but scheduled rather than lazy: a request
 * hitting a live yfinance round trip (11 sector ETF histories, one call at a time) on every cache
 * expiry was the slow path users noticed. Refreshing on a timer instead means requests only ever
 * read the last computed snapshot from MySQL -- if a refresh is slow, in-flight, or the collector
 * is briefly down, callers keep getting the previous (stale but fast) ranking rather than waiting
 * on or failing a live fetch.
 */
@Service
public class SectorPerformanceService {

    private static final Logger log = LoggerFactory.getLogger(SectorPerformanceService.class);

    private final CollectorClient collectorClient;
    private final SectorPerformanceSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public SectorPerformanceService(
            CollectorClient collectorClient, SectorPerformanceSnapshotRepository repository, ObjectMapper objectMapper) {
        this.collectorClient = collectorClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${sector-performance.cron}")
    @Transactional
    @CacheEvict(value = CacheConfig.SECTOR_PERFORMANCE, allEntries = true)
    public void refresh() {
        List<SectorPerformance> performance = collectorClient.getSectorPerformance();
        if (performance.isEmpty()) {
            // Collector/yfinance hiccup -- leave the previous snapshot in place rather than
            // overwrite it with nothing; the cache evict above still clears out to force the next
            // read to hit MySQL, which still has the last good data.
            log.warn("Sector performance refresh returned no data -- keeping previous snapshot");
            return;
        }

        String payloadJson = objectMapper.writeValueAsString(performance);
        SectorPerformanceSnapshot snapshot = repository.findTopByOrderByIdDesc().orElse(null);
        if (snapshot == null) {
            repository.save(SectorPerformanceSnapshot.builder().payloadJson(payloadJson).build());
        } else {
            snapshot.update(payloadJson);
        }
        log.info("Sector performance refreshed ({} sectors)", performance.size());
    }

    @Transactional(readOnly = true)
    @Cacheable(CacheConfig.SECTOR_PERFORMANCE)
    public List<SectorPerformance> getLatest() {
        SectorPerformanceSnapshot snapshot = repository
                .findTopByOrderByIdDesc()
                .orElseThrow(() -> new ResourceNotFoundException("Sector performance not computed yet"));
        return objectMapper.readValue(snapshot.getPayloadJson(), new TypeReference<List<SectorPerformance>>() {});
    }
}
