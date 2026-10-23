package org.juns.marketboardbackend.marketbreadth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.juns.marketboardbackend.collector.*;
import org.juns.marketboardbackend.marketbreadth.dto.MarketBreadthResponse;
import org.juns.marketboardbackend.pricehistory.*;
import org.juns.marketboardbackend.symbol.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketBreadthServiceTest {
    final SymbolRepository symbols = mock(SymbolRepository.class);
    final PriceHistoryRepository prices = mock(PriceHistoryRepository.class);
    final MarketBreadthSnapshotRepository snapshots = mock(MarketBreadthSnapshotRepository.class);
    final CollectorClient collector = mock(CollectorClient.class);
    final MarketBreadthService service = new MarketBreadthService(symbols, prices, snapshots, collector);

    void calendar() {
        when(collector.getCompletedMarketSession()).thenReturn(Optional.of(new CompletedMarketSession(
                "XNYS", "America/New_York", "test", Instant.now(), LocalDate.parse("2026-03-09"),
                Instant.parse("2026-03-09T20:00:00Z"),
                List.of(LocalDate.parse("2026-03-05"), LocalDate.parse("2026-03-06"), LocalDate.parse("2026-03-09")))));
    }

    Symbol symbol(long id) {
        Symbol s = mock(Symbol.class);
        when(s.getId()).thenReturn(id);
        return s;
    }

    PriceHistory bar(Symbol s, String ts, int close) {
        PriceHistory p = mock(PriceHistory.class);
        when(p.getSymbol()).thenReturn(s);
        when(p.getTs()).thenReturn(Instant.parse(ts));
        when(p.getClose()).thenReturn(BigDecimal.valueOf(close));
        return p;
    }

    @Test void alignedDatesExcludeStaleSymbolsOpenBarsAndUnknownTimestampConventions() {
        calendar();
        var good = symbol(1); var stale = symbol(2); var unknown = symbol(3);
        when(symbols.findByActiveTrueOrInSp500UniverseTrueOrderByPriorityAsc()).thenReturn(List.of(good, stale, unknown));
        var bars = List.of(
                bar(good, "2026-03-10T04:00:00Z", 1), // not yet a completed session
                bar(good, "2026-03-09T04:00:00Z", 110), // after DST
                bar(good, "2026-03-06T05:00:00Z", 100), // before DST
                bar(stale, "2026-03-06T05:00:00Z", 90), bar(stale, "2026-03-05T05:00:00Z", 100),
                bar(unknown, "2026-03-09T00:00:00Z", 120), bar(unknown, "2026-03-06T00:00:00Z", 100));
        when(prices.findBySymbol_IdInAndTimeframeAndTsGreaterThanEqual(anyList(), eq("1d"), any())).thenReturn(bars);
        when(snapshots.findBySnapshotDate(any())).thenReturn(Optional.empty());
        service.recompute();
        var captured = ArgumentCaptor.forClass(MarketBreadthSnapshot.class);
        verify(snapshots).save(captured.capture());
        var result = MarketBreadthResponse.from(captured.getValue());
        assertThat(result.tradingDate()).isEqualTo(LocalDate.parse("2026-03-09"));
        assertThat(result.advancingCount()).isEqualTo(1);
        assertThat(result.universeSize()).isEqualTo(1);
        assertThat(result.eligibleCount()).isEqualTo(3);
        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.source()).contains("provider metadata unavailable");
    }

    @Test void calendarFailureDoesNotOverwriteSnapshot() {
        when(collector.getCompletedMarketSession()).thenReturn(Optional.empty());
        service.recompute();
        verifyNoInteractions(symbols, prices, snapshots);
    }

    @Test void missingPreviousSessionDoesNotOverwriteSnapshot() {
        calendar(); var s = symbol(1);
        when(symbols.findByActiveTrueOrInSp500UniverseTrueOrderByPriorityAsc()).thenReturn(List.of(s));
        var bars = List.of(bar(s, "2026-03-09T04:00:00Z", 110), bar(s, "2026-03-05T05:00:00Z", 100));
        when(prices.findBySymbol_IdInAndTimeframeAndTsGreaterThanEqual(anyList(), eq("1d"), any())).thenReturn(bars);
        service.recompute();
        verifyNoInteractions(snapshots);
    }

    @Test void legacySnapshotDoesNotInventTradingDateOrSource() {
        var result = MarketBreadthResponse.from(MarketBreadthSnapshot.builder()
                .snapshotDate(LocalDate.parse("2026-03-10")).universeSize(10).build());
        assertThat(result.status()).isEqualTo("UNVERIFIED");
        assertThat(result.tradingDate()).isNull();
        assertThat(result.source()).isNull();
    }
}
