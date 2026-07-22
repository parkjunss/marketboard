package org.juns.marketboardbackend.sentiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.OptionsLevel;
import org.juns.marketboardbackend.collector.OptionsLevelsResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Same lazy per-ticker DB-cache behavior as PutCallRatioServiceTest's getForTicker coverage:
 * fresh cache avoids a live refetch, a stale cache triggers exactly one, and a failed refetch
 * falls back to whatever was last computed instead of failing outright.
 */
@SpringBootTest
@Transactional
class OptionsLevelsServiceTest {

    @Autowired
    private OptionsLevelsService optionsLevelsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private CollectorClient collectorClient;

    private static OptionsLevelsResponse sample(double maxPain) {
        return new OptionsLevelsResponse(
                "AAPL",
                "2026-07-24",
                324.5,
                maxPain,
                List.of(new OptionsLevel(330.0, 8215)),
                List.of(new OptionsLevel(315.0, 9584)));
    }

    @Test
    void fetchesLiveOnFirstCallThenServesTheCacheWithoutRefetching() {
        OptionsLevelsResponse response = sample(325.0);
        when(collectorClient.getOptionsLevels("AAPL")).thenReturn(Optional.of(response));

        assertThat(optionsLevelsService.getForTicker("aapl")).isEqualTo(response);
        assertThat(optionsLevelsService.getForTicker("AAPL")).isEqualTo(response);

        verify(collectorClient, times(1)).getOptionsLevels("AAPL");
    }

    @Test
    void refetchesOnceTheCacheIsStale() {
        when(collectorClient.getOptionsLevels("AAPL")).thenReturn(Optional.of(sample(325.0)));
        optionsLevelsService.getForTicker("AAPL");
        backdateComputedAt("AAPL", Instant.now().minus(31, ChronoUnit.MINUTES));

        OptionsLevelsResponse updated = sample(320.0);
        when(collectorClient.getOptionsLevels("AAPL")).thenReturn(Optional.of(updated));

        assertThat(optionsLevelsService.getForTicker("AAPL")).isEqualTo(updated);
    }

    @Test
    void servesStaleDataWhenTheLiveRefetchFails() {
        OptionsLevelsResponse firstGood = sample(325.0);
        when(collectorClient.getOptionsLevels("AAPL")).thenReturn(Optional.of(firstGood));
        optionsLevelsService.getForTicker("AAPL");
        backdateComputedAt("AAPL", Instant.now().minus(31, ChronoUnit.MINUTES));

        when(collectorClient.getOptionsLevels("AAPL")).thenReturn(Optional.empty());

        assertThat(optionsLevelsService.getForTicker("AAPL")).isEqualTo(firstGood);
    }

    @Test
    void throwsWhenNothingIsCachedAndTheLiveFetchFails() {
        when(collectorClient.getOptionsLevels("ZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> optionsLevelsService.getForTicker("ZZZZ")).isInstanceOf(ResourceNotFoundException.class);
    }

    private void backdateComputedAt(String ticker, Instant computedAt) {
        jdbcTemplate.update("UPDATE options_levels_snapshot SET computed_at = ? WHERE ticker = ?", computedAt, ticker);
        entityManager.flush();
        entityManager.clear();
    }
}
