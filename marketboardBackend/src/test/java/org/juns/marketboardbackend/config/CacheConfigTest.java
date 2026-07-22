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
        Object cached = cache.get("round-trip-test-record").get();

        assertThat(cached).isEqualTo(value);

        cache.evict("round-trip-test-record");
    }

    // Must be a plain ArrayList, not List.of() -- see the comment on CollectorClient's cached
    // methods for why List.of()'s immutable impl silently breaks this exact round trip.
    @Test
    void listValueRoundTripsWithConcreteElementType() {
        Cache cache = cacheManager.getCache(CacheConfig.MARKET_INDICES);
        List<MarketIndexInfo> value = new ArrayList<>(List.of(new MarketIndexInfo("spx", "S&P 500"), new MarketIndexInfo("ndx", "NASDAQ 100")));

        cache.put("round-trip-test-list", value);
        Object cached = cache.get("round-trip-test-list").get();

        assertThat(cached).isEqualTo(value);

        cache.evict("round-trip-test-list");
    }
}
