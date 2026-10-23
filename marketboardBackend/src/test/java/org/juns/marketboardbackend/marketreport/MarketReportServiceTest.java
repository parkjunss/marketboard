package org.juns.marketboardbackend.marketreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.juns.marketboardbackend.collector.*;
import org.juns.marketboardbackend.marketbreadth.MarketBreadthService;
import org.juns.marketboardbackend.marketindex.MarketIndexHistoryService;
import org.juns.marketboardbackend.news.NewsService;
import org.juns.marketboardbackend.sectorperformance.SectorPerformanceService;
import org.juns.marketboardbackend.sentiment.PutCallRatioService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "market-report.cron=-")
class MarketReportServiceTest {
    @Autowired MarketReportService service;
    @Autowired MarketReportRepository repository;
    @MockitoBean CollectorClient collector;
    @MockitoBean MarketIndexHistoryService prices;
    @MockitoBean SectorPerformanceService sectors;
    @MockitoBean MarketBreadthService breadth;
    @MockitoBean NewsService news;
    @MockitoBean PutCallRatioService putCall;
    final LocalDate date = LocalDate.parse("2026-03-09");

    @BeforeEach void setup() {
        repository.deleteAll();
        when(collector.getCompletedMarketSession()).thenReturn(Optional.of(new CompletedMarketSession("XNYS", "America/New_York", "test",
                Instant.now(), date, Instant.parse("2026-03-09T20:00:00Z"), List.of(date.minusDays(4), date.minusDays(3), date))));
        when(prices.getHistory(anyString())).thenReturn(List.of(candle("2026-03-06", 100), candle("2026-03-09", 105)));
        when(sectors.getLatest()).thenReturn(List.of());
        when(news.getGeneralNews()).thenReturn(List.of());
    }

    MarketIndexCandle candle(String day, double close) {
        return new MarketIndexCandle(day + "T04:00:00Z", close, close, close, close, 100,
                day, "2026-03-09T21:00:00Z", "test-provider", "test", "XNYS", "COMPLETED", "YFINANCE_AUTO_ADJUST");
    }

    @Test void savesPartialSnapshotAndReplayNeverRecapturesOrRewrites() {
        var first = service.generate();
        assertThat(first.state()).isEqualTo("PUBLISHED");
        var detail = service.get(first.reportId());
        assertThat(detail.report().status()).isEqualTo("PARTIAL");
        assertThat(detail.payload().indices()).hasSize(3);
        assertThat(detail.payload().indices().get(0).changePct1d()).isCloseTo(5, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(detail.payload().checks()).isNotEmpty();
        when(prices.getHistory(anyString())).thenThrow(new IllegalStateException("upstream unavailable"));
        clearInvocations(prices, sectors, news, breadth);
        var replay = service.generate();
        assertThat(replay.state()).isEqualTo("EXISTING");
        assertThat(replay.reportId()).isEqualTo(first.reportId());
        verifyNoInteractions(prices, sectors, news, breadth);
        assertThat(service.get(first.reportId())).isEqualTo(detail);
        assertThat(service.latest().state()).isEqualTo("CURRENT");
        assertThat(service.list(date.minusDays(1))).isEmpty();
        assertThat(service.list(date)).hasSize(1);
    }

    @Test void missingRequiredDateDefersWithoutPublishing() {
        when(prices.getHistory("SPX")).thenReturn(List.of(candle("2026-03-05", 100), candle("2026-03-06", 105)));
        assertThat(service.generate().state()).isEqualTo("DELAYED");
        assertThat(repository.count()).isZero();
        assertThat(service.latest().state()).isEqualTo("DELAYED");
    }

    @Test void calendarOutageDoesNotGuessDate() {
        when(collector.getCompletedMarketSession()).thenReturn(Optional.empty());
        assertThat(service.generate().tradingDate()).isNull();
        assertThat(service.latest().state()).isEqualTo("UNAVAILABLE");
        assertThat(repository.count()).isZero();
    }

    @Test void newSessionKeepsLastReportVisibleWhileDataIsDelayed() {
        var saved = service.generate();
        when(collector.getCompletedMarketSession()).thenReturn(Optional.of(new CompletedMarketSession("XNYS", "America/New_York", "test",
                Instant.now(), date.plusDays(1), Instant.parse("2026-03-10T20:00:00Z"), List.of(date.minusDays(3), date, date.plusDays(1)))));
        assertThat(service.generate().state()).isEqualTo("DELAYED");
        assertThat(service.latest().state()).isEqualTo("DELAYED");
        assertThat(service.latest().report().id()).isEqualTo(saved.reportId());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test void missingPreviousSessionDoesNotBecomeOneDayReturn() {
        when(prices.getHistory("SPX")).thenReturn(List.of(candle("2026-03-05", 100), candle("2026-03-09", 105)));
        assertThat(service.generate().state()).isEqualTo("DELAYED");
    }

    @Test void newsDeduplicatesAndExcludesFutureAndUnsafeLinks() {
        long at = Instant.parse("2026-03-09T18:00:00Z").getEpochSecond();
        when(news.getGeneralNews()).thenReturn(List.of(
                new NewsItem("", at, "Market update", 1, "", "", "Provider", "", "https://example.com/1"),
                new NewsItem("", at, "MARKET  UPDATE", 2, "", "", "Provider", "", "https://example.com/2"),
                new NewsItem("", at, "Unsafe", 3, "", "", "Provider", "", "javascript:alert(1)"),
                new NewsItem("", at + 86400, "Next day", 4, "", "", "Provider", "", "https://example.com/4")));
        var report = service.get(service.generate().reportId());
        assertThat(report.payload().news()).hasSize(1);
        assertThat(report.payload().news().get(0).headline()).isEqualTo("Market update");
    }

    @Test void concurrentGenerationCommitsOneImmutableRow() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<MarketReportService.Generation> generate = () -> { start.await(); return service.generate(); };
            var a = pool.submit(generate); var b = pool.submit(generate); start.countDown();
            var first = a.get(20, TimeUnit.SECONDS); var second = b.get(20, TimeUnit.SECONDS);
            assertThat(first.reportId()).isEqualTo(second.reportId());
            assertThat(repository.count()).isEqualTo(1);
            assertThat(List.of(first.state(), second.state())).contains("PUBLISHED", "EXISTING");
        } finally { pool.shutdownNow(); }
    }
}
