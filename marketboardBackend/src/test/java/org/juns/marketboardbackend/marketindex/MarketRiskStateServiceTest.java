package org.juns.marketboardbackend.marketindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.MarketRiskState;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

/** Requests read the last good snapshot; a failed or empty refresh never wipes it. */
class MarketRiskStateServiceTest {

    private final CollectorClient collectorClient = mock(CollectorClient.class);
    private final MarketRiskStateService service = new MarketRiskStateService(collectorClient);

    private static MarketRiskState state(String tradingDate, List<MarketRiskState.Asset> assets) {
        return new MarketRiskState(tradingDate, "2026-10-29T00:00:00Z", "Yahoo Finance via yfinance", "YFINANCE_AUTO_ADJUST",
                new MarketRiskState.Thresholds(70, 90, false), Map.of(), Map.of("NORMAL", assets.size()), assets);
    }

    private static MarketRiskState.Asset spy() {
        return new MarketRiskState.Asset("SPY", "S&P 500", "MULTI_ASSET", "READY", "NORMAL", 10.0, 27.8, 13.3, 1.0, 3.0,
                "1993-01-29", "2026-10-28");
    }

    @Test
    void notComputedYetIsNotFound() {
        assertThatThrownBy(service::getLatest).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void refreshStoresStateAndKeepsItWhenTheNextRefreshFailsOrIsEmpty() {
        MarketRiskState good = state("2026-10-28", List.of(spy()));
        when(collectorClient.getMarketRiskState()).thenReturn(Optional.of(good));
        service.refresh();

        when(collectorClient.getMarketRiskState()).thenReturn(Optional.empty());
        service.refresh();
        when(collectorClient.getMarketRiskState()).thenReturn(Optional.of(state("2026-10-29", List.of())));
        service.refresh();

        assertThat(service.getLatest()).isEqualTo(good);
    }
}
