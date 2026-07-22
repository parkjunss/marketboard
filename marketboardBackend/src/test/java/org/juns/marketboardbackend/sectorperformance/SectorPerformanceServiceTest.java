package org.juns.marketboardbackend.sectorperformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.SectorPerformance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The behavior the user actually asked for: a request never blocks on a live yfinance round trip,
 * and a failed/empty refresh must NOT wipe out the last good snapshot -- getLatest() should keep
 * serving the previous (stale but fast) ranking rather than 404 or return nothing.
 */
@SpringBootTest
@Transactional
class SectorPerformanceServiceTest {

    @Autowired
    private SectorPerformanceService sectorPerformanceService;

    @MockitoBean
    private CollectorClient collectorClient;

    @Test
    void refreshPersistsAndGetLatestReadsItBack() {
        List<SectorPerformance> performance = List.of(new SectorPerformance("XLK", "Technology", 1.0, 2.0, 3.0));
        when(collectorClient.getSectorPerformance()).thenReturn(performance);

        sectorPerformanceService.refresh();

        assertThat(sectorPerformanceService.getLatest()).isEqualTo(performance);
    }

    @Test
    void emptyRefreshKeepsThePreviousSnapshot() {
        List<SectorPerformance> firstGood = List.of(new SectorPerformance("XLK", "Technology", 1.0, 2.0, 3.0));
        when(collectorClient.getSectorPerformance()).thenReturn(firstGood);
        sectorPerformanceService.refresh();

        when(collectorClient.getSectorPerformance()).thenReturn(List.of());
        sectorPerformanceService.refresh();

        assertThat(sectorPerformanceService.getLatest()).isEqualTo(firstGood);
    }
}
