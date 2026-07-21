package org.juns.marketboardbackend.symbol;

import java.time.Duration;
import java.time.Instant;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.SymbolProfileResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-through cache in front of the collector's yfinance-backed /symbol-profile/{ticker} --
 * same reasoning as {@link org.juns.marketboardbackend.financials.FinancialsService}: a company's
 * sector/PER/employee count etc. don't change intraday, so a fresh yfinance round trip is only
 * worth it once the cached copy is older than CACHE_TTL. Also insulates the symbol detail page
 * from yfinance being slow or rate-limited on a given request -- a stale cache still beats a
 * multi-second (or hung) live lookup.
 */
@Service
public class SymbolProfileService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final SymbolProfileCacheRepository symbolProfileCacheRepository;
    private final CollectorClient collectorClient;
    private final ObjectMapper objectMapper;

    public SymbolProfileService(
            SymbolProfileCacheRepository symbolProfileCacheRepository,
            CollectorClient collectorClient,
            ObjectMapper objectMapper) {
        this.symbolProfileCacheRepository = symbolProfileCacheRepository;
        this.collectorClient = collectorClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SymbolProfileResponse getProfile(String ticker) {
        String normalized = ticker.toUpperCase();
        SymbolProfileCache cached = symbolProfileCacheRepository.findByTickerIgnoreCase(normalized).orElse(null);

        if (cached != null && isFresh(cached)) {
            return objectMapper.readValue(cached.getPayloadJson(), SymbolProfileResponse.class);
        }

        SymbolProfileResponse fetched = collectorClient.getSymbolProfile(normalized).orElse(null);
        if (fetched == null) {
            if (cached != null) {
                // Collector/yfinance hiccup (or rate-limited/slow) -- serve the stale cache
                // rather than fail outright.
                return objectMapper.readValue(cached.getPayloadJson(), SymbolProfileResponse.class);
            }
            throw new ResourceNotFoundException("Unknown ticker " + normalized);
        }

        String payloadJson = objectMapper.writeValueAsString(fetched);
        if (cached == null) {
            symbolProfileCacheRepository.save(
                    SymbolProfileCache.builder().ticker(normalized).payloadJson(payloadJson).build());
        } else {
            cached.update(payloadJson);
        }
        return fetched;
    }

    private boolean isFresh(SymbolProfileCache cache) {
        return cache.getFetchedAt().isAfter(Instant.now().minus(CACHE_TTL));
    }
}
