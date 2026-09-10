package org.juns.marketboardbackend.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.juns.marketboardbackend.portfolio.dto.PortfolioPositionResponse;
import org.juns.marketboardbackend.portfolio.dto.PortfolioSummaryResponse;
import org.juns.marketboardbackend.quote.ResolvedPrice;
import org.juns.marketboardbackend.symbol.Symbol;
import org.junit.jupiter.api.Test;

class PortfolioQualityTest {
    private PortfolioPositionResponse position(ResolvedPrice price) {
        PortfolioPosition p = mock(PortfolioPosition.class);
        Symbol symbol = mock(Symbol.class);
        when(p.getSymbol()).thenReturn(symbol);
        when(p.getQuantity()).thenReturn(new BigDecimal("2"));
        when(p.getAvgCost()).thenReturn(new BigDecimal("80"));
        return PortfolioPositionResponse.from(p, price);
    }

    @Test
    void partialValuationReportsCoverageAndDoesNotMixUnpricedCost() {
        var priced = position(new ResolvedPrice(new BigDecimal("100"), "LIVE", "FINNHUB", Instant.now(), null, null, "STALE"));
        var missing = position(null);
        var summary = PortfolioSummaryResponse.of(mock(Portfolio.class), List.of(priced, missing));
        assertThat(summary.valuationStatus()).isEqualTo("PARTIAL");
        assertThat(summary.pricedPositionCount()).isEqualTo(1);
        assertThat(summary.unpricedPositionCount()).isEqualTo(1);
        assertThat(summary.stalePositionCount()).isEqualTo(1);
        assertThat(summary.totalMarketValue()).isEqualByComparingTo("200");
        assertThat(summary.totalCostBasis()).isEqualByComparingTo("160");
        assertThat(summary.totalUnrealizedPnl()).isEqualByComparingTo("40");
    }

    @Test
    void noPricesAndEmptyPortfolioAreDistinct() {
        var missing = PortfolioSummaryResponse.of(mock(Portfolio.class), List.of(position(null)));
        assertThat(missing.valuationStatus()).isEqualTo("UNAVAILABLE");
        assertThat(missing.totalMarketValue()).isNull();
        assertThat(PortfolioSummaryResponse.of(mock(Portfolio.class), List.of()).valuationStatus()).isEqualTo("EMPTY");
    }

    @Test
    void completeCoverageDoesNotClaimFreshnessForUnverifiedPrices() {
        var p = position(new ResolvedPrice(new BigDecimal("100"), "CLOSE", "UNKNOWN", null, null,
                java.time.LocalDate.of(2026, 1, 2), "UNVERIFIED"));
        var summary = PortfolioSummaryResponse.of(mock(Portfolio.class), List.of(p));
        assertThat(summary.valuationStatus()).isEqualTo("UNVERIFIED");
        assertThat(summary.unverifiedPositionCount()).isEqualTo(1);
        assertThat(p.priceSessionDate()).isEqualTo(java.time.LocalDate.of(2026, 1, 2));
        var mapper = new tools.jackson.databind.ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(p));
        assertThat(json.get("priceStatus").asString()).isEqualTo("UNVERIFIED");
        assertThat(json.get("priceSessionDate").asString()).isEqualTo("2026-01-02");
    }
}
