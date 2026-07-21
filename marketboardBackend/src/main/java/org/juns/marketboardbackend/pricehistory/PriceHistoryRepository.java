package org.juns.marketboardbackend.pricehistory;

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

    void deleteBySymbol_Id(Long symbolId);
}
