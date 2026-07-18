package org.juns.marketboardbackend.indicator;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.juns.marketboardbackend.pricehistory.PriceHistory;
import org.juns.marketboardbackend.pricehistory.PriceHistoryRepository;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
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
        Timer.Sample sample = Timer.start(meterRegistry);
        for (Symbol symbol : symbols) {
            try {
                recomputeForSymbol(symbol);
                meterRegistry.counter("marketboard.indicators.recompute", "result", "success").increment();
            } catch (RuntimeException ex) {
                // Caught per-symbol so one bad symbol doesn't abort the whole batch (and its
                // failure is actually counted, rather than propagating out of the @Transactional
                // method and silently rolling back everyone else's successful upserts too).
                meterRegistry.counter("marketboard.indicators.recompute", "result", "failure").increment();
                log.warn("Failed to recompute indicators for {}: {}", symbol.getTicker(), ex.getMessage());
            }
        }
        sample.stop(meterRegistry.timer("marketboard.indicators.recompute.duration"));
        log.info("Recomputed indicators for {} symbol(s)", symbols.size());
    }

    private void recomputeForSymbol(Symbol symbol) {
        List<PriceHistory> candles = priceHistoryRepository.findBySymbol_TickerIgnoreCaseAndTimeframeOrderByTsDesc(
                symbol.getTicker(), TIMEFRAME, PageRequest.of(0, MAX_LOOKBACK));
        if (candles.isEmpty()) {
            return;
        }
        List<BigDecimal> closesOldestFirst = candles.stream()
                .sorted(Comparator.comparing(PriceHistory::getTs))
                .map(PriceHistory::getClose)
                .toList();

        upsert(symbol, IndicatorType.SMA20, TechnicalIndicators.sma(closesOldestFirst, 20));
        upsert(symbol, IndicatorType.SMA50, TechnicalIndicators.sma(closesOldestFirst, 50));
        upsert(symbol, IndicatorType.RSI14, TechnicalIndicators.rsi(closesOldestFirst, 14));
    }

    private void upsert(Symbol symbol, IndicatorType type, BigDecimal value) {
        if (value == null) {
            return; // not enough history yet for this indicator
        }
        indicatorRepository
                .findBySymbol_IdAndIndicatorTypeAndTimeframe(symbol.getId(), type, TIMEFRAME)
                .ifPresentOrElse(
                        indicator -> indicator.updateValue(value),
                        () -> indicatorRepository.save(Indicator.builder()
                                .symbol(symbol)
                                .indicatorType(type)
                                .timeframe(TIMEFRAME)
                                .value(value)
                                .build()));
    }
}
