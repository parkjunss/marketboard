package org.juns.marketboardbackend.marketindex;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Computes the in-memory risk state once at startup so a fresh deploy doesn't 404 until the next cron run. */
@Component
public class MarketRiskStateInitializer implements ApplicationRunner {

    private final MarketRiskStateService marketRiskStateService;

    public MarketRiskStateInitializer(MarketRiskStateService marketRiskStateService) {
        this.marketRiskStateService = marketRiskStateService;
    }

    @Override
    public void run(ApplicationArguments args) {
        marketRiskStateService.refresh();
    }
}
