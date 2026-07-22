package org.juns.marketboardbackend.indicator;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndicatorRepository extends JpaRepository<Indicator, Long> {

    List<Indicator> findBySymbol_TickerIgnoreCaseAndTimeframe(String ticker, String timeframe);

    Optional<Indicator> findBySymbol_IdAndIndicatorTypeAndTimeframe(
            Long symbolId, IndicatorType indicatorType, String timeframe);

    // Bulk-loads every symbol's existing indicators in one query so IndicatorCalculationService
    // can decide update-vs-insert from an in-memory map instead of one lookup per (symbol, type).
    List<Indicator> findBySymbol_IdInAndTimeframe(Collection<Long> symbolIds, String timeframe);

    void deleteBySymbol_Id(Long symbolId);
}
