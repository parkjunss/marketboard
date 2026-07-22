package org.juns.marketboardbackend.news;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Refreshes the general news snapshot once at startup, not just on NewsService's cron schedule --
 * otherwise a fresh deploy/restart leaves the news panel blank (getGeneralNews() 404s) for up to a
 * full cron interval. Kept as a separate bean (see SectorPerformanceInitializer for why: a
 * same-class self-invoked @EventListener would bypass the @Transactional/@CacheEvict proxy).
 */
@Component
public class NewsInitializer implements ApplicationRunner {

    private final NewsService newsService;

    public NewsInitializer(NewsService newsService) {
        this.newsService = newsService;
    }

    @Override
    public void run(ApplicationArguments args) {
        newsService.refresh();
    }
}
