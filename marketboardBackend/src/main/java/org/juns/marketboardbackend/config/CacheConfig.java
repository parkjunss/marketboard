package org.juns.marketboardbackend.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Read-through cache for data that's identical for every visitor: market indices/history/sector
 * performance and fear-greed/put-call-ratio (proxied from the Python collector's yfinance/CNN
 * calls, see CollectorClient) and the daily market-breadth snapshot (see MarketBreadthService).
 * Both /market (authenticated) and /overview (public, no login) render the same panels, so
 * without caching every page view re-triggers the full set of collector round trips. Boot 4.1
 * dropped the RedisCacheManager autoconfiguration + RedisCacheManagerBuilderCustomizer hook that
 * older Spring Boot versions provided (confirmed absent from spring-boot-autoconfigure-4.1.0's
 * cache package, which now only has CacheType left) -- so the manager is built by hand here
 * instead of via a customizer bean.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String MARKET_INDICES = "market-indices";
    public static final String MARKET_INDEX_HISTORY = "market-index-history";
    public static final String SECTOR_PERFORMANCE = "sector-performance";
    public static final String FEAR_GREED = "fear-greed";
    public static final String PUT_CALL_RATIO = "put-call-ratio";
    public static final String MARKET_BREADTH = "market-breadth";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer.UTF_8))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        cacheValueSerializer(objectMapper)));

        // Daily-cadence market data tolerates a longer TTL than the sentiment endpoints, which are
        // best-effort scrapes (CNN Fear & Greed, yfinance options) that can move intraday.
        Map<String, RedisCacheConfiguration> perCache = Map.of(
                MARKET_INDICES, base.entryTtl(Duration.ofMinutes(15)),
                MARKET_INDEX_HISTORY, base.entryTtl(Duration.ofMinutes(15)),
                SECTOR_PERFORMANCE, base.entryTtl(Duration.ofMinutes(15)),
                FEAR_GREED, base.entryTtl(Duration.ofMinutes(5)),
                PUT_CALL_RATIO, base.entryTtl(Duration.ofMinutes(5)),
                MARKET_BREADTH, base.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    /**
     * GenericJacksonJsonRedisSerializer always serializes/deserializes with a static type of
     * Object (RedisCache only ever asks for "the value back", never a concrete class). Plain
     * {@code ObjectMapper.rebuild().activateDefaultTyping(...)} does NOT cover this: Jackson's
     * DefaultTyping policies only add type info when a *property's declared type* (per bean
     * introspection) is Object/non-concrete -- a bare root call like
     * {@code mapper.writeValueAsBytes(value)} has no such enclosing property, so it resolves the
     * "declared type" as value.getClass() and writes plain JSON with no type info. The read side
     * then asks for Object.class explicitly, expects type info to be there, and either throws
     * (WRAPPER_ARRAY: "expected START_ARRAY") or silently returns a LinkedHashMap instead of the
     * real DTO (surfacing later as a ClassCastException at the @Cacheable call site). The
     * serializer's own builder + enableDefaultTyping() sidesteps this with a root-aware
     * TypeResolverBuilder built for exactly this "always Object" scenario. Its mapper is rebuilt
     * from the app's shared ObjectMapper (not a fresh JsonMapper) so it keeps every module Boot
     * already configured; the type validator is scoped to our own DTO package plus java.util (the
     * List/Map wrapper types those DTOs are cached inside).
     */
    private static GenericJacksonJsonRedisSerializer cacheValueSerializer(ObjectMapper objectMapper) {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("org.juns.marketboardbackend.")
                .allowIfSubType("java.util.")
                .build();
        return GenericJacksonJsonRedisSerializer.builder(() -> ((JsonMapper) objectMapper).rebuild())
                .enableDefaultTyping(typeValidator)
                .build();
    }
}
