package org.juns.marketboardbackend.review;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.juns.marketboardbackend.backtest.dto.EtfComparisonRequest;
import org.juns.marketboardbackend.collector.*;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class EtfPaperServiceTest {
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    ObjectMapper mapper = new ObjectMapper();
    ReviewService reviews = mock(ReviewService.class);
    CollectorClient collector = mock(CollectorClient.class);
    EtfPaperService service;

    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE investment_reviews(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO users VALUES(1),(2)");
        jdbc.update("INSERT INTO investment_reviews VALUES(10)");
        for (var sql : Files.readString(Path.of("src/main/resources/db/migration/V38__create_etf_paper_accounts.sql")).split(";"))
            if (!sql.isBlank()) jdbc.execute(sql);
        service = new EtfPaperService(jdbc, mapper, reviews, collector);
        var config = new EtfComparisonRequest(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31),
                new BigDecimal("10000"), new BigDecimal("5"), new BigDecimal("5"));
        var payload = new ReviewService.Payload(4, "etf-momentum-v1", 21, Instant.now(), Instant.now(),
                Map.of(), null, null, Map.of(), null, null,
                new EtfResearchEvidence("etf-momentum-v1", "MOMENTUM_TREND", config, null, null));
        when(reviews.get(1L, 10L)).thenReturn(new ReviewService.Detail(10L, 21, Instant.now(), payload));
        when(reviews.decisions(1L, 10L)).thenReturn(List.of(new ReviewService.Decision(1L, 10L,
                ReviewDecision.Choice.EXECUTE, "검토 완료", null, Instant.now())));
        when(collector.getEtfMomentum()).thenReturn(Optional.of(new EtfMomentumResult(
                LocalDate.now(ZoneId.of("America/New_York")).withDayOfMonth(1).minusDays(1),
                null, null, true, List.of(), BigDecimal.ONE)));
        when(collector.advanceEtfPaper(any())).thenAnswer(invocation -> {
            Map<?, ?> request = invocation.getArgument(0);
            return Optional.of(mapper.valueToTree(Map.of("state", request.get("state"), "events", List.of(), "message", "대기")));
        });
    }
    private EtfPaperService.Account start() {
        return transaction.execute(status -> service.start(1L, 10L));
    }
    @Test void persistsOneSignalAndReplayRefreshNeverRewritesIt() {
        var account = start();
        var first = service.snapshots(1L, account.id());
        transaction.execute(status -> service.refresh(1L, account.id()));
        assertThat(service.snapshots(1L, account.id())).isEqualTo(first);
        assertThat(first).hasSize(1);
        assertThat(account.state().path("cash").asInt()).isEqualTo(10000);
        verify(collector, times(1)).getEtfMomentum();
    }
    @Test void otherOwnerCannotReadRefreshOrStop() {
        var account = start();
        assertThat(service.list(2L)).isEmpty();
        assertThatThrownBy(() -> service.snapshots(2L, account.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> transaction.execute(status -> service.refresh(2L, account.id()))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> transaction.execute(status -> service.stop(2L, account.id()))).isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.list(1L).get(0).active()).isTrue();
    }
    @Test void stopPreservesJournalAndPreventsFurtherCalculations() {
        var account = start(); var before = service.snapshots(1L, account.id());
        transaction.execute(status -> service.stop(1L, account.id()));
        clearInvocations(collector);
        var result = transaction.execute(status -> service.refresh(1L, account.id()));
        assertThat(result.active()).isFalse();
        assertThat(service.snapshots(1L, account.id())).isEqualTo(before);
        verifyNoInteractions(collector);
    }
    @Test void collectorOutagePreservesCapturedSignalAndState() {
        doReturn(Optional.empty()).when(collector).advanceEtfPaper(any());
        var account = start();
        assertThat(account.state().path("cash").asInt()).isEqualTo(10000);
        assertThat(account.message()).contains("기존 기록");
        assertThat(service.snapshots(1L, account.id())).hasSize(1);
    }
    @Test void adoptionRequiredBeforeInsert() {
        when(reviews.decisions(1L, 10L)).thenReturn(List.of());
        assertThatThrownBy(this::start).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.list(1L)).isEmpty();
        verifyNoInteractions(collector);
    }
    @Test void oldReviewPayloadStillDeserializes() {
        var old = "{\"schemaVersion\":3,\"calculationVersion\":\"old\",\"period\":21,\"histories\":{},\"positions\":{}}";
        assertThat(mapper.readValue(old, ReviewService.Payload.class).etf()).isNull();
    }
    @Test void valuationPersistsStateTradesAndInputPricesTogether() {
        var account = start();
        var state = mapper.valueToTree(Map.of("date", LocalDate.now().toString(), "cash", 9000,
                "shares", Map.of("SPY", 10), "value", 10000, "returnPct", 0));
        var event = Map.of("state", state, "trades", List.of(Map.of("ticker", "SPY", "quantity", 10)),
                "prices", Map.of("SPY", Map.of("open", 100, "close", 100)));
        doReturn(Optional.of(mapper.valueToTree(Map.of("state", state, "events", List.of(event), "message", ""))))
                .when(collector).advanceEtfPaper(any());
        transaction.execute(status -> service.refresh(1L, account.id()));
        assertThat(service.list(1L).get(0).state()).isEqualTo(state);
        var saved = service.snapshots(1L, account.id());
        assertThat(saved).hasSize(2);
        assertThat(saved.get(1).kind()).isEqualTo("VALUATION");
        assertThat(saved.get(1).payload().path("prices").path("SPY").path("close").asInt()).isEqualTo(100);
        assertThat(saved.get(1).payload().path("trades").size()).isEqualTo(1);
    }
}
