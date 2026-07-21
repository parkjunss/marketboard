package org.juns.marketboardbackend.marketbreadth;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.marketbreadth.dto.MarketBreadthResponse;
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

/**
 * Market-wide advance/decline and 52-week high/low counts over the S&P 500 + active-symbol
 * universe. Derived purely from daily bars already in price_history, so this only needs
 * recomputing once a day (same reasoning as IndicatorCalculationService, just a daily cadence
 * instead of every 5 minutes since there's no intraday signal to catch up on).
 */
@Service
public class MarketBreadthService {

    private static final Logger log = LoggerFactory.getLogger(MarketBreadthService.class);
    private static final String TIMEFRAME = "1d";
    private static final int WINDOW = 252; // ~52 trading weeks

    private final SymbolRepository symbolRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketBreadthSnapshotRepository marketBreadthSnapshotRepository;

    public MarketBreadthService(
            SymbolRepository symbolRepository,
            PriceHistoryRepository priceHistoryRepository,
            MarketBreadthSnapshotRepository marketBreadthSnapshotRepository) {
        this.symbolRepository = symbolRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.marketBreadthSnapshotRepository = marketBreadthSnapshotRepository;
    }

    @Scheduled(cron = "${market-breadth.cron}")
    @Transactional
    public void recompute() {
        List<Symbol> universe = symbolRepository.findByActiveTrueOrInSp500UniverseTrueOrderByPriorityAsc();

        int advancing = 0;
        int declining = 0;
        int unchanged = 0;
        int newHighs = 0;
        int newLows = 0;
        int counted = 0;

        for (Symbol symbol : universe) {
            List<PriceHistory> candles = priceHistoryRepository.findBySymbol_IdAndTimeframeOrderByTsDesc(
                    symbol.getId(), TIMEFRAME, PageRequest.of(0, WINDOW));
            if (candles.size() < 2) {
                continue; // not enough history yet (e.g. just activated, not backfilled) -- skip rather than count it wrong
            }
            counted++;

            BigDecimal latest = candles.get(0).getClose();
            BigDecimal previous = candles.get(1).getClose();
            int cmp = latest.compareTo(previous);
            if (cmp > 0) {
                advancing++;
            } else if (cmp < 0) {
                declining++;
            } else {
                unchanged++;
            }

            BigDecimal windowHigh = candles.stream().map(PriceHistory::getClose).max(Comparator.naturalOrder()).orElse(latest);
            BigDecimal windowLow = candles.stream().map(PriceHistory::getClose).min(Comparator.naturalOrder()).orElse(latest);
            if (latest.compareTo(windowHigh) >= 0) {
                newHighs++;
            }
            if (latest.compareTo(windowLow) <= 0) {
                newLows++;
            }
        }

        LocalDate today = LocalDate.now();
        MarketBreadthSnapshot snapshot = marketBreadthSnapshotRepository.findBySnapshotDate(today).orElse(null);
        if (snapshot == null) {
            snapshot = MarketBreadthSnapshot.builder()
                    .snapshotDate(today)
                    .advancingCount(advancing)
                    .decliningCount(declining)
                    .unchangedCount(unchanged)
                    .new52wHighCount(newHighs)
                    .new52wLowCount(newLows)
                    .universeSize(counted)
                    .build();
            marketBreadthSnapshotRepository.save(snapshot);
        } else {
            snapshot.update(advancing, declining, unchanged, newHighs, newLows, counted);
        }
        log.info(
                "Market breadth recomputed: {} advancing / {} declining / {} unchanged, {} new 52w highs / {} new 52w lows (of {})",
                advancing, declining, unchanged, newHighs, newLows, counted);
    }

    @Transactional(readOnly = true)
    public MarketBreadthResponse getLatest() {
        return marketBreadthSnapshotRepository
                .findTopByOrderBySnapshotDateDesc()
                .map(MarketBreadthResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No market breadth snapshot computed yet"));
    }
}
