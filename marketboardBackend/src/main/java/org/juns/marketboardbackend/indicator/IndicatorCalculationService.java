package org.juns.marketboardbackend.indicator;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.juns.marketboardbackend.pricehistory.PriceHistory;
import org.juns.marketboardbackend.pricehistory.PriceHistoryRepository;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndicatorCalculationService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorCalculationService.class);
    private static final String TIMEFRAME = "1d";
    private static final int MAX_LOOKBACK = 60; // enough history for SMA50 + RSI14

    private final SymbolRepository symbolRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final IndicatorRepository indicatorRepository;
    private final MeterRegistry meterRegistry;

    public IndicatorCalculationService(
            SymbolRepository symbolRepository,
            PriceHistoryRepository priceHistoryRepository,
            IndicatorRepository indicatorRepository,
            MeterRegistry meterRegistry) {
        this.symbolRepository = symbolRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.indicatorRepository = indicatorRepository;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "${indicators.cron}")
    @Transactional
    public void recomputeAll() {
        // Covers both the real-time WS set (active) and the batch-only S&P 500 universe — the
        // latter's daily bars only change once a day, so most of these recomputes are no-ops
        // between backfills, but that's cheap enough not to warrant a separate schedule.
        List<Symbol> symbols = symbolRepository.findByActiveTrueOrInSp500UniverseTrueOrderByPriorityAsc();
        if (symbols.isEmpty()) {
            return;
        }
        List<Long> symbolIds = symbols.stream().map(Symbol::getId).toList();

        // Two bulk reads instead of ~symbols.size() * 4 individual round trips (one candle lookup
        // + up to 3 indicator lookups each) -- this ran every 5 minutes over ~500 symbols, so that
        // was ~2000 DB round trips per run. Grouped into maps here so the per-symbol loop below is
        // pure in-memory work.
        Map<Long, List<PriceHistory>> candlesBySymbol = priceHistoryRepository
                .findBySymbol_IdInAndTimeframeAndTsGreaterThanEqual(symbolIds, TIMEFRAME, lookbackCutoff())
                .stream()
                .collect(Collectors.groupingBy(candle -> candle.getSymbol().getId()));

        Map<Long, Map<IndicatorType, Indicator>> indicatorsBySymbol = indicatorRepository
                .findBySymbol_IdInAndTimeframe(symbolIds, TIMEFRAME)
                .stream()
                .collect(Collectors.groupingBy(
                        indicator -> indicator.getSymbol().getId(),
                        Collectors.toMap(Indicator::getIndicatorType, Function.identity())));

        Timer.Sample sample = Timer.start(meterRegistry);
        List<Indicator> newIndicators = new ArrayList<>();
        for (Symbol symbol : symbols) {
            try {
                recomputeForSymbol(
                        symbol,
                        candlesBySymbol.getOrDefault(symbol.getId(), List.of()),
                        indicatorsBySymbol.getOrDefault(symbol.getId(), Map.of()),
                        newIndicators);
                meterRegistry.counter("marketboard.indicators.recompute", "result", "success").increment();
            } catch (RuntimeException ex) {
                // Caught per-symbol so one bad symbol doesn't abort the whole batch (and its
                // failure is actually counted, rather than propagating out of the @Transactional
                // method and silently rolling back everyone else's successful upserts too).
                meterRegistry.counter("marketboard.indicators.recompute", "result", "failure").increment();
                log.warn("Failed to recompute indicators for {}: {}", symbol.getTicker(), ex.getMessage());
            }
        }
        indicatorRepository.saveAll(newIndicators);
        sample.stop(meterRegistry.timer("marketboard.indicators.recompute.duration"));
        log.info("Recomputed indicators for {} symbol(s)", symbols.size());
    }

    // MAX_LOOKBACK trading days padded out to calendar days (weekends + holidays) with generous
    // headroom -- daily bars mean at most one row per symbol per calendar day, so this comfortably
    // covers MAX_LOOKBACK rows without risking an under-fetch that silently shrinks the SMA/RSI
    // window.
    private static Instant lookbackCutoff() {
        return Instant.now().minus(MAX_LOOKBACK * 3L, ChronoUnit.DAYS);
    }

    private void recomputeForSymbol(
            Symbol symbol,
            List<PriceHistory> candles,
            Map<IndicatorType, Indicator> existingIndicators,
            List<Indicator> newIndicators) {
        if (candles.isEmpty()) {
            return;
        }
        List<BigDecimal> closesOldestFirst = candles.stream()
                .sorted(Comparator.comparing(PriceHistory::getTs))
                .map(PriceHistory::getClose)
                .toList();
        // The bulk query above fetches by date cutoff, not "last MAX_LOOKBACK rows", so trim down
        // to the same window the old per-symbol Pageable(0, MAX_LOOKBACK) query used to return.
        if (closesOldestFirst.size() > MAX_LOOKBACK) {
            closesOldestFirst = closesOldestFirst.subList(closesOldestFirst.size() - MAX_LOOKBACK, closesOldestFirst.size());
        }

        upsert(symbol, IndicatorType.SMA20, TechnicalIndicators.sma(closesOldestFirst, 20), existingIndicators, newIndicators);
        upsert(symbol, IndicatorType.SMA50, TechnicalIndicators.sma(closesOldestFirst, 50), existingIndicators, newIndicators);
        upsert(symbol, IndicatorType.RSI14, TechnicalIndicators.rsi(closesOldestFirst, 14), existingIndicators, newIndicators);
    }

    private void upsert(
            Symbol symbol,
            IndicatorType type,
            BigDecimal value,
            Map<IndicatorType, Indicator> existingIndicators,
            List<Indicator> newIndicators) {
        if (value == null) {
            return; // not enough history yet for this indicator
        }
        Indicator existing = existingIndicators.get(type);
        if (existing != null) {
            existing.updateValue(value); // managed entity -- flushed via dirty checking at commit
        } else {
            newIndicators.add(Indicator.builder()
                    .symbol(symbol)
                    .indicatorType(type)
                    .timeframe(TIMEFRAME)
                    .value(value)
                    .build());
        }
    }
}
