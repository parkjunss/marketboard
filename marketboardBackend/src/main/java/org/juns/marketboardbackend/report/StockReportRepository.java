package org.juns.marketboardbackend.report;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReportRepository extends JpaRepository<StockReport, Long> {
    List<StockReport> findTop50ByUserIdAndTickerOrderByIdDesc(Long userId, String ticker);
    Optional<StockReport> findByIdAndUserId(Long id, Long userId);
}
