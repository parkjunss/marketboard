package org.juns.marketboardbackend.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "marketboard.portfolio-ledger",
        name = "opening-backfill-enabled",
        havingValue = "true")
public class PortfolioOpeningBalanceBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PortfolioOpeningBalanceBackfillRunner.class);
    private final PortfolioOpeningBalanceBackfillService service;

    public PortfolioOpeningBalanceBackfillRunner(PortfolioOpeningBalanceBackfillService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        var result = service.backfill();
        log.info("Portfolio opening-balance backfill completed: total={}, existing={}, inserted={}, "
                        + "quantity={}, cost={}, completedAt={}",
                result.totalPositions(), result.alreadyMigrated(), result.inserted(),
                result.totalQuantity(), result.totalCost(), result.completedAt());
    }
}
