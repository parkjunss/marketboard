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
                Map.of("price", "123.45", "volume", "1000", "ts", Instant.now().toString()));

        jdbcTemplate.update(
                "INSERT INTO price_history (symbol_id, ts, open, high, low, close, volume, timeframe) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                staleSymbol.getId(), Instant.now().minus(1, ChronoUnit.DAYS), "50.0000", "50.0000", "50.0000", "50.0000", 1_000_000L, "1d");

        Map<String, ResolvedPrice> resolved = quoteService.resolvePrices(List.of("live", "stale", "missing"));

        assertThat(resolved.get("LIVE")).isEqualTo(new ResolvedPrice(new BigDecimal("123.45"), true));
        assertThat(resolved.get("STALE")).isEqualTo(new ResolvedPrice(new BigDecimal("50.0000"), false));
        assertThat(resolved).doesNotContainKey("MISSING");
    }
}
