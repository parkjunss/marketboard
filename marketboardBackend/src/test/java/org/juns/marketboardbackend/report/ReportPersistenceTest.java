package org.juns.marketboardbackend.report;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.juns.marketboardbackend.analysis.AnalysisService;
import org.juns.marketboardbackend.financials.FinancialsService;
import org.juns.marketboardbackend.quote.QuoteService;
import org.juns.marketboardbackend.quote.ResolvedPrice;
import org.juns.marketboardbackend.common.IdempotencyService;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.user.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Import({ReportService.class, IdempotencyService.class, ReportPersistenceTest.MapperConfig.class})
class ReportPersistenceTest {
    @TestConfiguration static class MapperConfig {
        @Bean ObjectMapper mapper() { return JsonMapper.builder().findAndAddModules().build(); }
    }
    @Autowired ReportService reports;
    @Autowired StockReportRepository repository;
    @Autowired UserRepository users;
    @Autowired IdempotencyService idempotency;
    @MockitoBean QuoteService quotes;
    @MockitoBean FinancialsService financials;
    @MockitoBean AnalysisService analysis;

    @Test void storesIndependentVersionsAndReplaysWithoutDuplicating() {
        var user = users.saveAndFlush(User.builder().email("report-test@example.com").username("report-test")
                .passwordHash("unused").role(Role.USER).build());
        var now = Instant.parse("2026-09-13T01:00:00Z");
        var payload = new ReportService.Payload(1, "stock-report-v1", "NVDA", now, now,
                new ReportService.Resource<>(new ResolvedPrice(new BigDecimal("100"), "CLOSE", "YFINANCE", now, now, null, "STALE"), null), new ReportService.Resource<>(List.of(), null),
                new ReportService.Resource<>(null, "자료 없음"), new ReportService.Resource<>(null, "자료 없음"), List.of("partial evidence"));
        String key = UUID.randomUUID().toString();
        var first = idempotency.execute(user.getId(), "stock-report-create", key, "NVDA", ReportService.Detail.class,
                () -> reports.save(user.getId(), payload));
        var replay = idempotency.execute(user.getId(), "stock-report-create", key, "NVDA", ReportService.Detail.class,
                () -> { throw new AssertionError("Replay must not save"); });
        var second = reports.save(user.getId(), payload);
        assertThat(replay).isEqualTo(first);
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(reports.get(user.getId(), first.id()).payload()).isEqualTo(payload);
        assertThat(reports.list(user.getId(), "nvda")).extracting(ReportService.Summary::id).containsExactly(second.id(), first.id());
        assertThatThrownBy(() -> reports.get(user.getId() + 1, first.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(repository.count()).isEqualTo(2);
    }
}
