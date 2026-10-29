package org.juns.marketboardbackend.marketindex;

import java.util.concurrent.atomic.AtomicReference;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.MarketRiskState;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled snapshot of the collector's volatility risk state, same "requests never wait on a live
 * yfinance round trip" reasoning as SectorPerformanceService. Held in memory rather than MySQL: the
 * state is fully recomputed from public prices each run, so a restart only needs one refresh
 * (MarketRiskStateInitializer) instead of a migration-backed table.
 */
@Service
public class MarketRiskStateService {

    private static final Logger log = LoggerFactory.getLogger(MarketRiskStateService.class);

    private final CollectorClient collectorClient;
    private final AtomicReference<MarketRiskState> latest = new AtomicReference<>();

    public MarketRiskStateService(CollectorClient collectorClient) {
        this.collectorClient = collectorClient;
    }

    @Scheduled(cron = "${market-risk.cron}")
    public void refresh() {
        collectorClient.getMarketRiskState()
                .filter(state -> state.assets() != null && !state.assets().isEmpty())
                .ifPresentOrElse(state -> {
                    latest.set(state);
                    log.info("Market risk state refreshed ({} assets, {})", state.assets().size(), state.expectedTradingDate());
                }, () -> log.warn("Market risk state refresh returned no data -- keeping previous snapshot"));
    }

    public MarketRiskState getLatest() {
        MarketRiskState state = latest.get();
        if (state == null) {
            throw new ResourceNotFoundException("Market risk state not computed yet");
        }
        return state;
    }
}
