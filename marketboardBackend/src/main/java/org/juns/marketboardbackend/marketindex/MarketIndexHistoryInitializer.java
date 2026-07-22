package org.juns.marketboardbackend.marketindex;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Refreshes market index history once at startup, not just on MarketIndexHistoryService's cron
 * schedule -- otherwise a fresh deploy/restart leaves every index chart blank (getHistory() 404s)
 * for up to a full cron interval. Kept as a separate bean (see SectorPerformanceInitializer for
 * why: a same-class self-invoked @EventListener would bypass the @Transactional/@CacheEvict proxy).
 */
@Component
public class MarketIndexHistoryInitializer implements ApplicationRunner {

    private final MarketIndexHistoryService marketIndexHistoryService;

    public MarketIndexHistoryInitializer(MarketIndexHistoryService marketIndexHistoryService) {
        this.marketIndexHistoryService = marketIndexHistoryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        marketIndexHistoryService.refresh();
    }
}
