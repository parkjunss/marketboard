package org.juns.marketboardbackend.pricehistory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    // Queried by symbol_id, not ticker -- symbol_id is the leading column of the
    // (symbol_id, timeframe, ts) unique index, so this is a direct index range scan. Going
    // through Symbol_TickerIgnoreCase instead forces a JOIN + ORDER BY across the whole table
    // (MySQL can't push the ticker filter down before sorting), which was a full table scan +
    // filesort on every call once price_history grew past a few hundred thousand rows.
    List<PriceHistory> findBySymbol_IdAndTimeframeOrderByTsDesc(Long symbolId, String timeframe, Pageable pageable);

    Optional<PriceHistory> findFirstBySymbol_IdAndTimeframeOrderByTsDesc(Long symbolId, String timeframe);

    // One bulk range scan for every symbol's recent candles, used by IndicatorCalculationService
    // to avoid a per-symbol round trip (503 symbols x 1 query used to run every 5 minutes). Still
    // an index range scan on (symbol_id, timeframe, ts) per symbol_id in the IN-list, just all
    // sent as one statement instead of one round trip each.
    List<PriceHistory> findBySymbol_IdInAndTimeframeAndTsGreaterThanEqual(
            Collection<Long> symbolIds, String timeframe, Instant since);

    void deleteBySymbol_Id(Long symbolId);
}
