package org.juns.marketboardbackend.sentiment;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.OptionsLevelsResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-through cache in front of the collector's CBOE-backed options support/resistance levels
 * (max pain + open-interest-ranked strikes) -- same lazy per-ticker pattern as
 * PutCallRatioService.getForTicker()/SymbolProfileService: refreshed on request with a TTL rather
 * than proactively for every tracked symbol, since only tickers someone actually looks at need
 * this computed at all.
 */
@Service
public class OptionsLevelsService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final CollectorClient collectorClient;
    private final OptionsLevelsSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public OptionsLevelsService(
            CollectorClient collectorClient, OptionsLevelsSnapshotRepository repository, ObjectMapper objectMapper) {
        this.collectorClient = collectorClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OptionsLevelsResponse getForTicker(String ticker) {
        String normalized = ticker.toUpperCase();
        OptionsLevelsSnapshot cached = repository.findByTickerIgnoreCase(normalized).orElse(null);

        if (cached != null && isFresh(cached)) {
            return deserialize(cached);
        }

        Optional<OptionsLevelsResponse> fetched = collectorClient.getOptionsLevels(normalized);
        if (fetched.isEmpty()) {
            if (cached != null) {
                // Collector/CBOE hiccup (or a ticker CBOE has no listing for) -- serve the stale
                // cache rather than fail outright.
                return deserialize(cached);
            }
            throw new ResourceNotFoundException("No options data available for " + normalized);
        }

        String payloadJson = objectMapper.writeValueAsString(fetched.get());
        if (cached == null) {
            repository.save(OptionsLevelsSnapshot.builder().ticker(normalized).payloadJson(payloadJson).build());
        } else {
            cached.update(payloadJson);
        }
        return fetched.get();
    }

    private boolean isFresh(OptionsLevelsSnapshot snapshot) {
        return snapshot.getComputedAt().isAfter(Instant.now().minus(CACHE_TTL));
    }

    private OptionsLevelsResponse deserialize(OptionsLevelsSnapshot snapshot) {
        return objectMapper.readValue(snapshot.getPayloadJson(), OptionsLevelsResponse.class);
    }
}
