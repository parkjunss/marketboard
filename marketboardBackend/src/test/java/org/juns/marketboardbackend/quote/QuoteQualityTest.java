package org.juns.marketboardbackend.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.juns.marketboardbackend.pricehistory.PriceHistoryRepository;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class QuoteQualityTest {
    @SuppressWarnings("unchecked")
    private QuoteService service(Map<Object, Object> fields) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries("quote:AAA")).thenReturn(fields);
        return new QuoteService(redis, mock(SymbolRepository.class), mock(PriceHistoryRepository.class));
    }

    @Test
    void legacyPollingTimestampDoesNotImplyRecentTrade() {
        ResolvedPrice price = service(Map.of("price", "100", "ts", Instant.now().toString()))
                .resolvePrice("AAA").orElseThrow();
        assertThat(price.status()).isEqualTo("UNVERIFIED");
        assertThat(price.asOf()).isNull();
        assertThat(price.isLive()).isFalse();
    }

    @Test
    void restFetchTimeIsNotObservationTime() {
        String fetched = Instant.now().toString();
        ResolvedPrice price = service(Map.of("price", "100", "source", "YFINANCE", "ts", "", "fetchedAt", fetched))
                .resolvePrice("AAA").orElseThrow();
        assertThat(price.asOf()).isNull();
        assertThat(price.fetchedAt()).isEqualTo(Instant.parse(fetched));
        assertThat(price.provider()).isEqualTo("YFINANCE");
        assertThat(price.status()).isEqualTo("UNVERIFIED");
    }

    @Test
    void oldTickIsMarkedStale() {
        ResolvedPrice price = service(Map.of("price", "100", "source", "FINNHUB", "ts", Instant.now().minusSeconds(600).toString()))
                .resolvePrice("AAA").orElseThrow();
        assertThat(price.status()).isEqualTo("STALE");
        assertThat(price.isLive()).isFalse();
    }

    @Test
    void malformedAndNegativePricesAreUnavailable() {
        for (String invalid : List.of("NaN", "-1", "0", "broken")) {
            assertThat(service(Map.of("price", invalid)).resolvePrice("AAA")).isEmpty();
        }
    }

    @Test
    void invalidOrFutureTradeTimeCannotBeRecent() {
        for (String invalid : List.of("broken", Instant.now().plusSeconds(600).toString())) {
            assertThat(service(Map.of("price", "100", "source", "FINNHUB", "ts", invalid))
                    .resolvePrice("AAA").orElseThrow().status()).isEqualTo("UNVERIFIED");
        }
    }
}
