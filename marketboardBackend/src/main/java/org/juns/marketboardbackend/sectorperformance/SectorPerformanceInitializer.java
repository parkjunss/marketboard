package org.juns.marketboardbackend.sectorperformance;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Refreshes the sector-rotation snapshot once at startup, not just on SectorPerformanceService's
 * cron schedule -- otherwise a fresh deploy/restart leaves the sector table blank (getLatest()
 * 404s, frontend renders nothing) for up to a full cron interval. A separate bean (rather than
 * SectorPerformanceService calling its own refresh() from an @EventListener method on itself) so
 * the call goes through Spring's proxy and @Transactional/@CacheEvict actually apply -- a
 * same-class self-invocation would silently skip both.
 */
@Component
public class SectorPerformanceInitializer implements ApplicationRunner {

    private final SectorPerformanceService sectorPerformanceService;

    public SectorPerformanceInitializer(SectorPerformanceService sectorPerformanceService) {
        this.sectorPerformanceService = sectorPerformanceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        sectorPerformanceService.refresh();
    }
}
