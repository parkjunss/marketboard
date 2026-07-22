package org.juns.marketboardbackend.sentiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.PutCallRatioResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Same behavior SectorPerformanceServiceTest locks in: a request never blocks on a live yfinance
 * options-chain fetch, and a failed/empty refresh must NOT wipe out the last good snapshot.
 */
@SpringBootTest
@Transactional
class PutCallRatioServiceTest {

    @Autowired
    private PutCallRatioService putCallRatioService;

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
}
