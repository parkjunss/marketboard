package org.juns.marketboardbackend.news;

import java.util.List;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.NewsItem;
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
 * Read-through cache in front of the collector's general news feed, same "scheduled refresh +
 * MySQL snapshot" pattern as SectorPerformanceService/PutCallRatioService. Company news
 * (per-ticker, effectively unbounded key space across every tracked symbol) stays on the lazy
 * Redis cache CollectorClient.getCompanyNews() already had -- proactively refreshing news for
 * every possible ticker on a schedule isn't proportionate the way it is for a single global feed.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final CollectorClient collectorClient;
    private final NewsSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public NewsService(CollectorClient collectorClient, NewsSnapshotRepository repository, ObjectMapper objectMapper) {
        this.collectorClient = collectorClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${news.cron}")
    @Transactional
    @CacheEvict(value = CacheConfig.NEWS_GENERAL, allEntries = true)
    public void refresh() {
        List<NewsItem> news = collectorClient.getGeneralNews();
        if (news.isEmpty()) {
            // Collector hiccup -- leave the previous snapshot in place rather than overwrite it
            // with nothing; the cache evict above still clears out to force the next read to hit
            // MySQL, which still has the last good data.
            log.warn("News refresh returned no data -- keeping previous snapshot");
            return;
        }

        String payloadJson = objectMapper.writeValueAsString(news);
        NewsSnapshot snapshot = repository.findTopByOrderByIdDesc().orElse(null);
        if (snapshot == null) {
            repository.save(NewsSnapshot.builder().payloadJson(payloadJson).build());
        } else {
            snapshot.update(payloadJson);
        }
        log.info("News refreshed ({} items)", news.size());
    }

    @Transactional(readOnly = true)
    @Cacheable(CacheConfig.NEWS_GENERAL)
    public List<NewsItem> getGeneralNews() {
        NewsSnapshot snapshot = repository
                .findTopByOrderByIdDesc()
                .orElseThrow(() -> new ResourceNotFoundException("News not fetched yet"));
        return objectMapper.readValue(snapshot.getPayloadJson(), new TypeReference<List<NewsItem>>() {});
    }
}
