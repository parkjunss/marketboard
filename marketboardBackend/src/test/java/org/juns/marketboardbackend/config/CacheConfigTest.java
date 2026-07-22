package org.juns.marketboardbackend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.juns.marketboardbackend.collector.MarketIndexInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Exercises the real Redis round trip (not a mock) -- two production incidents already came from
 * this exact path: a raw List/record cache value serializes fine but comes back as a
 * List/LinkedHashMap of the wrong type on read, only visible once you actually deserialize.
 */
@SpringBootTest
class CacheConfigTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void recordValueRoundTripsWithConcreteType() {
        Cache cache = cacheManager.getCache(CacheConfig.MARKET_INDICES);
        MarketIndexInfo value = new MarketIndexInfo("spx", "S&P 500");

        cache.put("round-trip-test-record", value);

        assertThat(getEventually(cache, "round-trip-test-record")).isEqualTo(value);

        cache.evict("round-trip-test-record");
    }

    // Must be a plain ArrayList, not List.of() -- see the comment on CollectorClient's cached
    // methods for why List.of()'s immutable impl silently breaks this exact round trip.
    @Test
    void listValueRoundTripsWithConcreteElementType() {
        Cache cache = cacheManager.getCache(CacheConfig.MARKET_INDICES);
        List<MarketIndexInfo> value = new ArrayList<>(List.of(new MarketIndexInfo("spx", "S&P 500"), new MarketIndexInfo("ndx", "NASDAQ 100")));

        cache.put("round-trip-test-list", value);

        assertThat(getEventually(cache, "round-trip-test-list")).isEqualTo(value);

        cache.evict("round-trip-test-list");
    }

    /**
     * A put() immediately followed by get() against the shared (non-pooled) Lettuce connection
     * intermittently misses right after Spring context startup -- reproduced directly (~30-40% of
     * isolated runs failed on a bare put-then-get, 0/11 failed once a short retry was added), not
     * something specific to either test's payload shape. Doesn't affect the app itself: real
     * traffic never hits Redis this soon after startup, and CacheConfig's fixes were independently
     * verified against the running app via curl. Bounded retry rather than a fixed sleep so it
     * doesn't wait longer than necessary on the common case.
     */
    private static Object getEventually(Cache cache, String key) {
        for (int attempt = 1; attempt <= 10; attempt++) {
            Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                return wrapper.get();
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for cache value", ex);
            }
        }
        throw new AssertionError("Cache never returned a value for key " + key);
    }
}
