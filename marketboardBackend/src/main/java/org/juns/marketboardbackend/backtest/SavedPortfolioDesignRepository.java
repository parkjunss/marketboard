package org.juns.marketboardbackend.backtest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedPortfolioDesignRepository extends JpaRepository<SavedPortfolioDesign, Long> {
    interface SummaryRow {
        Long getId();
        String getName();
        String getSelectedId();
        java.time.Instant getCreatedAt();
    }
    List<SummaryRow> findTop50ByUserIdOrderByIdDesc(Long userId);
    Optional<SavedPortfolioDesign> findByIdAndUserId(Long id, Long userId);
}
