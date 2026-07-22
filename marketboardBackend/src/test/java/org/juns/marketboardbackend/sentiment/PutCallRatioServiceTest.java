package org.juns.marketboardbackend.sentiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.PutCallRatioResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers both halves of PutCallRatioService: SPY's scheduled refresh (same behavior
 * SectorPerformanceServiceTest locks in -- a request never blocks on a live yfinance
 * options-chain fetch, and a failed/empty refresh must NOT wipe out the last good snapshot) and
 * the lazy per-ticker path individual stock detail pages use (freshness-checked DB cache, same
 * pattern as SymbolProfileService).
 */
@SpringBootTest
@Transactional
class PutCallRatioServiceTest {

    @Autowired
    private PutCallRatioService putCallRatioService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private CollectorClient collectorClient;

    @Test
    void refreshPersistsAndGetLatestReadsItBack() {
        PutCallRatioResponse response = new PutCallRatioResponse("SPY", 8, 1_000_000, 800_000, 0.8);
        when(collectorClient.getPutCallRatio("SPY")).thenReturn(Optional.of(response));

        putCallRatioService.refresh();

        assertThat(putCallRatioService.getLatest()).isEqualTo(response);
    }

    @Test
    void emptyRefreshKeepsThePreviousSnapshot() {
        PutCallRatioResponse firstGood = new PutCallRatioResponse("SPY", 8, 1_000_000, 800_000, 0.8);
        when(collectorClient.getPutCallRatio("SPY")).thenReturn(Optional.of(firstGood));
        putCallRatioService.refresh();

        when(collectorClient.getPutCallRatio("SPY")).thenReturn(Optional.empty());
        putCallRatioService.refresh();

        assertThat(putCallRatioService.getLatest()).isEqualTo(firstGood);
    }

    @Test
    void getForTickerFetchesLiveOnFirstCallThenServesTheCacheWithoutRefetching() {
        PutCallRatioResponse response = new PutCallRatioResponse("AAPL", 8, 500_000, 400_000, 0.8);
        when(collectorClient.getPutCallRatio("AAPL")).thenReturn(Optional.of(response));

        assertThat(putCallRatioService.getForTicker("aapl")).isEqualTo(response);
        assertThat(putCallRatioService.getForTicker("AAPL")).isEqualTo(response);

        verify(collectorClient, times(1)).getPutCallRatio("AAPL");
    }

    @Test
    void getForTickerRefetchesOnceTheCacheIsStale() {
        PutCallRatioResponse firstGood = new PutCallRatioResponse("AAPL", 8, 500_000, 400_000, 0.8);
        when(collectorClient.getPutCallRatio("AAPL")).thenReturn(Optional.of(firstGood));
        putCallRatioService.getForTicker("AAPL");
        backdateComputedAt("AAPL", Instant.now().minus(31, ChronoUnit.MINUTES));

        PutCallRatioResponse updated = new PutCallRatioResponse("AAPL", 6, 600_000, 300_000, 0.5);
        when(collectorClient.getPutCallRatio("AAPL")).thenReturn(Optional.of(updated));

        assertThat(putCallRatioService.getForTicker("AAPL")).isEqualTo(updated);
    }

    @Test
    void getForTickerServesStaleDataWhenTheLiveRefetchFails() {
        PutCallRatioResponse firstGood = new PutCallRatioResponse("AAPL", 8, 500_000, 400_000, 0.8);
        when(collectorClient.getPutCallRatio("AAPL")).thenReturn(Optional.of(firstGood));
        putCallRatioService.getForTicker("AAPL");
        backdateComputedAt("AAPL", Instant.now().minus(31, ChronoUnit.MINUTES));

        when(collectorClient.getPutCallRatio("AAPL")).thenReturn(Optional.empty());

        assertThat(putCallRatioService.getForTicker("AAPL")).isEqualTo(firstGood);
    }

    @Test
    void getForTickerThrowsWhenNothingIsCachedAndTheLiveFetchFails() {
        when(collectorClient.getPutCallRatio("ZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> putCallRatioService.getForTicker("ZZZZ")).isInstanceOf(ResourceNotFoundException.class);
    }

    private void backdateComputedAt(String ticker, Instant computedAt) {
        jdbcTemplate.update("UPDATE put_call_ratio_snapshot SET computed_at = ? WHERE ticker = ?", computedAt, ticker);
        // The row just saved by getForTicker() is still managed in this test's (transactional)
        // persistence context, so the next findByTickerIgnoreCase() would otherwise return that
        // same in-memory instance -- with the pre-backdate computedAt -- instead of re-reading the
        // row this raw JDBC update just changed.
        entityManager.flush();
        entityManager.clear();
    }
}
