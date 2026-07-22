package org.juns.marketboardbackend.marketindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.MarketIndexCandle;
import org.juns.marketboardbackend.collector.MarketIndexInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Same behavior SectorPerformanceServiceTest/PutCallRatioServiceTest lock in: a request never
 * blocks on a live yfinance fetch, a failed refresh for one index doesn't wipe out its previous
 * snapshot, and each index's history is independent of the others.
 */
@SpringBootTest
@Transactional
class MarketIndexHistoryServiceTest {

    @Autowired
    private MarketIndexHistoryService marketIndexHistoryService;

    @MockitoBean
    private CollectorClient collectorClient;

    @Test
    void refreshPersistsPerIndexAndGetHistoryReadsItBack() {
        when(collectorClient.getMarketIndices()).thenReturn(List.of(new MarketIndexInfo("SPX", "S&P 500"), new MarketIndexInfo("VIX", "Volatility Index")));
        List<MarketIndexCandle> spxCandles = List.of(new MarketIndexCandle("2026-07-21T00:00:00Z", 100, 101, 99, 100.5, 1000));
        List<MarketIndexCandle> vixCandles = List.of(new MarketIndexCandle("2026-07-21T00:00:00Z", 15, 16, 14, 15.5, 500));
        when(collectorClient.getMarketIndexHistory("SPX")).thenReturn(spxCandles);
        when(collectorClient.getMarketIndexHistory("VIX")).thenReturn(vixCandles);

        marketIndexHistoryService.refresh();

        assertThat(marketIndexHistoryService.getHistory("SPX")).isEqualTo(spxCandles);
        assertThat(marketIndexHistoryService.getHistory("VIX")).isEqualTo(vixCandles);
    }

    @Test
    void emptyRefreshForOneIndexKeepsItsPreviousSnapshot() {
        when(collectorClient.getMarketIndices()).thenReturn(List.of(new MarketIndexInfo("SPX", "S&P 500")));
        List<MarketIndexCandle> firstGood = List.of(new MarketIndexCandle("2026-07-21T00:00:00Z", 100, 101, 99, 100.5, 1000));
        when(collectorClient.getMarketIndexHistory("SPX")).thenReturn(firstGood);
        marketIndexHistoryService.refresh();

        when(collectorClient.getMarketIndexHistory("SPX")).thenReturn(List.of());
        marketIndexHistoryService.refresh();

        assertThat(marketIndexHistoryService.getHistory("SPX")).isEqualTo(firstGood);
    }
}
