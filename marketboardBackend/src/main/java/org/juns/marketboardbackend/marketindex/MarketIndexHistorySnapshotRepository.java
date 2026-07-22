package org.juns.marketboardbackend.marketindex;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketIndexHistorySnapshotRepository extends JpaRepository<MarketIndexHistorySnapshot, Long> {

    Optional<MarketIndexHistorySnapshot> findBySlug(String slug);
}
