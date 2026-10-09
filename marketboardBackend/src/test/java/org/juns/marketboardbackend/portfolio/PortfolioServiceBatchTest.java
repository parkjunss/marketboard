package org.juns.marketboardbackend.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.juns.marketboardbackend.quote.QuoteService;
import org.juns.marketboardbackend.quote.ResolvedPrice;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolResolutionService;
import org.juns.marketboardbackend.user.UserRepository;
import org.junit.jupiter.api.Test;

class PortfolioServiceBatchTest {

    @Test
    void loadsAllPortfolioPricesInOneBatch() {
        PortfolioRepository portfolios = mock(PortfolioRepository.class);
        PortfolioPositionRepository positions = mock(PortfolioPositionRepository.class);
        QuoteService quotes = mock(QuoteService.class);
        PortfolioService service = new PortfolioService(
                portfolios, positions, mock(UserRepository.class), mock(SymbolResolutionService.class), quotes,
                mock(PortfolioTransactionRepository.class));

        Portfolio first = portfolio(1L, "First");
        Portfolio second = portfolio(2L, "Second");
        Symbol symbol = mock(Symbol.class);
        when(symbol.getId()).thenReturn(10L);
        when(symbol.getTicker()).thenReturn("AAA");
        when(symbol.getName()).thenReturn("AAA Inc");
        when(portfolios.findByUser_IdOrderByCreatedAtAsc(7L)).thenReturn(List.of(first, second));
        List<PortfolioPosition> storedPositions = List.of(
                position(11L, first, symbol), position(12L, second, symbol));
        when(positions.findAllByUserId(7L)).thenReturn(storedPositions);
        when(quotes.resolvePrices(List.of("AAA"))).thenReturn(Map.of("AAA", new ResolvedPrice(
                new BigDecimal("100"), "CLOSE", "YFINANCE", null, null,
                LocalDate.of(2026, 10, 8), "RECENT")));

        var result = service.getPortfolios(7L);

        assertThat(result).extracting(row -> row.positionCount()).containsExactly(1, 1);
        verify(quotes).resolvePrices(List.of("AAA"));
        verify(positions).findAllByUserId(7L);
        verify(positions, never()).findByPortfolio_IdOrderByIdAsc(anyLong());
    }

    private Portfolio portfolio(Long id, String name) {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolio.getId()).thenReturn(id);
        when(portfolio.getName()).thenReturn(name);
        when(portfolio.getCreatedAt()).thenReturn(Instant.EPOCH);
        when(portfolio.getUpdatedAt()).thenReturn(Instant.EPOCH);
        return portfolio;
    }

    private PortfolioPosition position(Long id, Portfolio portfolio, Symbol symbol) {
        PortfolioPosition position = mock(PortfolioPosition.class);
        when(position.getId()).thenReturn(id);
        when(position.getPortfolio()).thenReturn(portfolio);
        when(position.getSymbol()).thenReturn(symbol);
        when(position.getQuantity()).thenReturn(BigDecimal.ONE);
        when(position.getAvgCost()).thenReturn(new BigDecimal("80"));
        return position;
    }
}
