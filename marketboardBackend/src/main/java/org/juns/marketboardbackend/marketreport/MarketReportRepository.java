package org.juns.marketboardbackend.marketreport;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketReportRepository extends JpaRepository<MarketReport, Long> {
    Optional<MarketReport> findByMarketAndTradingDateAndVersion(String market, LocalDate date, int version);
    Optional<MarketReport> findTopByMarketOrderByTradingDateDescVersionDesc(String market);
    List<MarketReport> findByMarketAndTradingDateLessThanEqualOrderByTradingDateDescVersionDesc(
            String market, LocalDate before, Pageable pageable);
}
