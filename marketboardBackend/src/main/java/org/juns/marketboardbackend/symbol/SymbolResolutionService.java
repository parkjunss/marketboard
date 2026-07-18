package org.juns.marketboardbackend.symbol;

import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.SymbolProfileResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an arbitrary user-typed ticker to a {@link Symbol}, creating one on the fly via the
 * collector's yfinance-backed profile lookup if it isn't already known. New symbols are created
 * inactive: they aren't seeded into the real-time Finnhub WS subscription set, since a portfolio
 * position shouldn't silently expand what the collector subscribes to.
 */
@Service
public class SymbolResolutionService {

    private final SymbolRepository symbolRepository;
    private final CollectorClient collectorClient;

    public SymbolResolutionService(SymbolRepository symbolRepository, CollectorClient collectorClient) {
        this.symbolRepository = symbolRepository;
        this.collectorClient = collectorClient;
    }

    @Transactional
    public Symbol resolveOrFetch(String ticker) {
        String normalized = ticker.toUpperCase();
        return symbolRepository.findByTickerIgnoreCase(normalized).orElseGet(() -> fetchAndCreate(normalized));
    }

    private Symbol fetchAndCreate(String ticker) {
        SymbolProfileResponse profile = collectorClient.getSymbolProfile(ticker)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown ticker " + ticker));
        Symbol symbol = Symbol.builder()
                .ticker(ticker)
                .name(profile.name())
                .exchange(profile.exchange() != null ? profile.exchange() : "UNKNOWN")
                .priority(0)
                .build();
        symbol.deactivate();
        return symbolRepository.save(symbol);
    }
}
