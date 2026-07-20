package org.juns.marketboardbackend.symbol;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.juns.marketboardbackend.alert.AlertRepository;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.indicator.IndicatorRepository;
import org.juns.marketboardbackend.portfolio.PortfolioPositionRepository;
import org.juns.marketboardbackend.pricehistory.PriceHistoryRepository;
import org.juns.marketboardbackend.watchlist.WatchlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SymbolAdminServiceTest {

    @Mock
    private SymbolRepository symbolRepository;

    @Mock
    private CollectorClient collectorClient;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private IndicatorRepository indicatorRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private WatchlistItemRepository watchlistItemRepository;

    @Mock
    private PortfolioPositionRepository portfolioPositionRepository;

    private SymbolAdminService symbolAdminService;

    @BeforeEach
    void setUp() {
        symbolAdminService = new SymbolAdminService(
                symbolRepository, collectorClient, messagingTemplate,
                priceHistoryRepository, indicatorRepository, alertRepository,
                watchlistItemRepository, portfolioPositionRepository);
    }

    @Test
    void delete_existingSymbol_removesAllReferencingRowsThenTheSymbol() {
        Symbol symbol = Symbol.builder().ticker("AMZN").name("Amazon").exchange("NASDAQ").priority(99).build();
        ReflectionTestUtils.setField(symbol, "id", 7L);
        when(symbolRepository.findById(7L)).thenReturn(Optional.of(symbol));

        symbolAdminService.delete(7L);

        verify(priceHistoryRepository).deleteBySymbol_Id(7L);
        verify(indicatorRepository).deleteBySymbol_Id(7L);
        verify(alertRepository).deleteBySymbol_Id(7L);
        verify(watchlistItemRepository).deleteBySymbol_Id(7L);
        verify(portfolioPositionRepository).deleteBySymbol_Id(7L);
        verify(symbolRepository, times(1)).delete(eq(symbol));
    }

    @Test
    void delete_unknownSymbol_throwsAndDeletesNothing() {
        when(symbolRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> symbolAdminService.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(priceHistoryRepository, never()).deleteBySymbol_Id(404L);
        verify(symbolRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
