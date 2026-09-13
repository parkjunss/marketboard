package org.juns.marketboardbackend.report;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.juns.marketboardbackend.analysis.AnalysisService;
import org.juns.marketboardbackend.financials.*;
import org.juns.marketboardbackend.quote.*;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ReportServiceTest {
    final StockReportRepository repository = mock(StockReportRepository.class);
    final QuoteService quotes = mock(QuoteService.class);
    final FinancialsService financials = mock(FinancialsService.class);
    final FinancialStatementRepository statements = mock(FinancialStatementRepository.class);
    final AnalysisService analysis = mock(AnalysisService.class);
    final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    final ReportService service = new ReportService(repository, quotes, financials, statements, analysis, mapper);

    @Test void rejectsInvalidTickerBeforeAccessingSources() {
        assertThatThrownBy(() -> service.capture("../../NVDA")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(quotes, financials, analysis);
        assertThat(ReportService.normalize(" nvda ")).isEqualTo("NVDA");
    }
    @Test void preservesPartialEvidenceAndStalePriceWarning() {
        var price = new ResolvedPrice(new BigDecimal("100"), "CACHED", "UNKNOWN", Instant.EPOCH, null, null, "STALE");
        when(quotes.resolvePrice("NVDA")).thenReturn(Optional.of(price));
        when(financials.getFinancials("NVDA")).thenThrow(new RuntimeException("private upstream error"));
        when(analysis.analyze("NVDA", 252, 21, 500)).thenThrow(new RuntimeException("offline"));
        var payload = service.capture("nvda");
        assertThat(payload.price().data()).isEqualTo(price);
        assertThat(payload.financials().error()).isEqualTo("생성 당시 자료 조회 실패");
        assertThat(payload.checks()).anyMatch(s -> s.contains("가격이 오래"));
        assertThat(payload.checks()).anyMatch(s -> s.contains("정량 위험"));
        assertThat(mapper.readValue(mapper.writeValueAsString(payload), ReportService.Payload.class)).isEqualTo(payload);
    }
    @Test void rejectsReportWithNoUsableEvidence() {
        var payload = service.capture("NVDA");
        assertThatThrownBy(() -> service.save(1L, payload)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(repository);
    }
    @Test void cannotReadAnotherUsersReport() {
        when(repository.findByIdAndUserId(12L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(2L, 12L)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository).findByIdAndUserId(12L, 2L);
    }
    @Test void savedPayloadDoesNotChangeWhenSourcesChange() {
        var price = new ResolvedPrice(new BigDecimal("100"), "CLOSE", "YFINANCE", Instant.EPOCH, null, null, "STALE");
        when(quotes.resolvePrice("NVDA")).thenReturn(Optional.of(price));
        var payload = service.capture("NVDA");
        var row = new StockReport(1L, "NVDA", mapper.writeValueAsString(payload));
        when(repository.findByIdAndUserId(12L, 1L)).thenReturn(Optional.of(row));
        reset(quotes, financials, analysis);
        assertThat(service.get(1L, 12L).payload()).isEqualTo(payload);
        verifyNoInteractions(quotes, financials, analysis);
    }
}
