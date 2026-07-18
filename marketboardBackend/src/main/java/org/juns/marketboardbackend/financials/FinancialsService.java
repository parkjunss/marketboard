package org.juns.marketboardbackend.financials;

import java.time.Duration;
import java.time.Instant;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.FinancialsResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-through cache in front of the collector's yfinance-backed /financials/{ticker} — annual
 * statements don't change intraday, so a fresh fetch is only worth the yfinance round trip once
 * the cached copy is older than CACHE_TTL.
 */
@Service
public class FinancialsService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final FinancialStatementRepository financialStatementRepository;
    private final CollectorClient collectorClient;
    private final ObjectMapper objectMapper;

    public FinancialsService(
            FinancialStatementRepository financialStatementRepository,
            CollectorClient collectorClient,
            ObjectMapper objectMapper) {
        this.financialStatementRepository = financialStatementRepository;
        this.collectorClient = collectorClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FinancialsResponse getFinancials(String ticker) {
        String normalized = ticker.toUpperCase();
        FinancialStatement cached = financialStatementRepository.findByTickerIgnoreCase(normalized).orElse(null);

        if (cached != null && isFresh(cached)) {
            return objectMapper.readValue(cached.getPayloadJson(), FinancialsResponse.class);
        }

        FinancialsResponse fetched = collectorClient.getFinancials(normalized).orElse(null);
        if (fetched == null) {
            if (cached != null) {
                // Collector/yfinance hiccup — serve the stale cache rather than fail outright.
                return objectMapper.readValue(cached.getPayloadJson(), FinancialsResponse.class);
            }
            throw new ResourceNotFoundException("No financials available for " + normalized);
        }

        String payloadJson = objectMapper.writeValueAsString(fetched);
        if (cached == null) {
            financialStatementRepository.save(
                    FinancialStatement.builder().ticker(normalized).payloadJson(payloadJson).build());
        } else {
            cached.update(payloadJson);
        }
        return fetched;
    }

    private boolean isFresh(FinancialStatement statement) {
        return statement.getFetchedAt().isAfter(Instant.now().minus(CACHE_TTL));
    }
}
