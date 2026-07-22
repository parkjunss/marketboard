package org.juns.marketboardbackend.sentiment;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Refreshes the put/call ratio snapshot once at startup, not just on PutCallRatioService's cron
 * schedule -- otherwise a fresh deploy/restart leaves the sentiment card blank (getLatest() 404s)
 * for up to a full cron interval. Kept as a separate bean (see SectorPerformanceInitializer for
 * why: a same-class self-invoked @EventListener would bypass the @Transactional/@CacheEvict proxy).
 */
@Component
public class PutCallRatioInitializer implements ApplicationRunner {

    private final PutCallRatioService putCallRatioService;

    public PutCallRatioInitializer(PutCallRatioService putCallRatioService) {
        this.putCallRatioService = putCallRatioService;
    }

    @Override
    public void run(ApplicationArguments args) {
        putCallRatioService.refresh();
    }
}
