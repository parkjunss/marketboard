package org.juns.marketboardbackend.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers the bulk resolvePrices() rewrite -- a portfolio mixes tickers with a live Redis tick,
 * tickers falling back to the latest daily close, and tickers with no price at all, and each must
 * be resolved (or correctly omitted) without one DB round trip per position.
 */
@SpringBootTest
@Transactional
class QuoteServiceTest {

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private SymbolRepository symbolRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void cleanUpRedis() {
        stringRedisTemplate.delete("quote:LIVE");
    }

    @Test
    void resolvesLiveDbFallbackAndMissingTickersInOneBulkCall() {
        symbolRepository.save(Symbol.builder().ticker("LIVE").name("Live Co").exchange("NASDAQ").priority(1).build());
        Symbol staleSymbol = symbolRepository.save(Symbol.builder().ticker("STALE").name("Stale Co").exchange("NASDAQ").priority(2).build());

        stringRedisTemplate.opsForHash().putAll("quote:LIVE",
                Map.of("price", "123.45", "volume", "1000", "ts", Instant.now().toString(), "source", "FINNHUB"));

        jdbcTemplate.update(
                "INSERT INTO price_history (symbol_id, ts, open, high, low, close, volume, timeframe) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                staleSymbol.getId(), Instant.now().minus(1, ChronoUnit.DAYS), "50.0000", "50.0000", "50.0000", "50.0000", 1_000_000L, "1d");

        Map<String, ResolvedPrice> resolved = quoteService.resolvePrices(List.of("live", "stale", "missing"));

        assertThat(resolved.get("LIVE").price()).isEqualByComparingTo("123.45");
        assertThat(resolved.get("LIVE").status()).isEqualTo("RECENT");
        assertThat(resolved.get("STALE").price()).isEqualByComparingTo("50.0000");
        assertThat(resolved.get("STALE").status()).isEqualTo("UNVERIFIED");
        assertThat(resolved.get("STALE").sessionDate()).isNotNull();
        assertThat(resolved).doesNotContainKey("MISSING");
    }

    @Test
    void oldDailyHistoryRemainsVisibleAndSingleMatchesBulk() {
        Symbol symbol = symbolRepository.save(Symbol.builder().ticker("OLD").name("Old Co").exchange("NASDAQ").priority(3).build());
        Instant ts = Instant.now().minus(30, ChronoUnit.DAYS);
        jdbcTemplate.update(
                "INSERT INTO price_history (symbol_id, ts, open, high, low, close, volume, timeframe) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                symbol.getId(), ts, "50", "50", "50", "50", 100L, "1d");
        ResolvedPrice single = quoteService.resolvePrice("old").orElseThrow();
        assertThat(single).isEqualTo(quoteService.resolvePrices(List.of("OLD")).get("OLD"));
        assertThat(single.status()).isEqualTo("UNVERIFIED");
        assertThat(single.sessionDate()).isEqualTo(ts.atZone(java.time.ZoneId.of("America/New_York")).toLocalDate());
        assertThat(single.asOf()).isNull();
    }

    @Test
    void newerDailyBarWinsOverAnOldCachedTick() {
        Symbol symbol = symbolRepository.save(Symbol.builder().ticker("LIVE").name("Live Co").exchange("NASDAQ").priority(1).build());
        stringRedisTemplate.opsForHash().putAll("quote:LIVE", Map.of("price", "999", "volume", "1",
                "source", "FINNHUB", "ts", Instant.now().minus(3, ChronoUnit.DAYS).toString()));
        jdbcTemplate.update(
                "INSERT INTO price_history (symbol_id, ts, open, high, low, close, volume, timeframe) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                symbol.getId(), Instant.now().minus(1, ChronoUnit.DAYS), "50", "50", "50", "50", 100L, "1d");
        ResolvedPrice price = quoteService.resolvePrice("LIVE").orElseThrow();
        assertThat(price.price()).isEqualByComparingTo("50");
        assertThat(price.source()).isEqualTo("CLOSE");
        assertThat(price.status()).isEqualTo("UNVERIFIED");
    }
}
