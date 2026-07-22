package org.juns.marketboardbackend.indicator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers the bulk-fetch rewrite of recomputeAll() -- price_history is written directly by the
 * Python collector (PriceHistory has no builder/setters on the Java side), so rows are seeded
 * here via JdbcTemplate rather than the entity API.
 */
@SpringBootTest
@Transactional
class IndicatorCalculationServiceTest {

    @Autowired
    private IndicatorCalculationService indicatorCalculationService;

    @Autowired
    private SymbolRepository symbolRepository;

    @Autowired
    private IndicatorRepository indicatorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void recomputesForSymbolsWithEnoughHistoryAndSkipsThinOnes() {
        Symbol withHistory = symbolRepository.save(Symbol.builder().ticker("AAA").name("Triple A").exchange("NASDAQ").priority(1).build());
        Symbol thinHistory = symbolRepository.save(Symbol.builder().ticker("BBB").name("Double B").exchange("NASDAQ").priority(2).build());

        seedDailyCloses(withHistory.getId(), 55, "100.0000");
        seedDailyCloses(thinHistory.getId(), 10, "100.0000");

        // Pre-existing stale indicator -- must be updated in place, not duplicated.
        indicatorRepository.save(Indicator.builder()
                .symbol(withHistory)
                .indicatorType(IndicatorType.SMA20)
                .timeframe("1d")
                .value(new BigDecimal("50.0000"))
                .build());

        indicatorCalculationService.recomputeAll();

        List<Indicator> withHistoryIndicators = indicatorRepository.findBySymbol_TickerIgnoreCaseAndTimeframe("AAA", "1d");
        assertThat(withHistoryIndicators).hasSize(3);
        assertThat(withHistoryIndicators)
                .allSatisfy(indicator -> assertThat(indicator.getValue()).isEqualByComparingTo("100.0000"));

        assertThat(indicatorRepository.findBySymbol_TickerIgnoreCaseAndTimeframe("BBB", "1d")).isEmpty();
    }

    private void seedDailyCloses(Long symbolId, int days, String close) {
        Instant start = Instant.now().minus(days, ChronoUnit.DAYS);
        for (int i = 0; i < days; i++) {
            Instant ts = start.plus(i, ChronoUnit.DAYS);
            jdbcTemplate.update(
                    "INSERT INTO price_history (symbol_id, ts, open, high, low, close, volume, timeframe) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    symbolId, ts, close, close, close, close, 1_000_000L, "1d");
        }
    }
}
