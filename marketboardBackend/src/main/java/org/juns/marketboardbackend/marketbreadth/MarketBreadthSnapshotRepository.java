package org.juns.marketboardbackend.marketbreadth;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketBreadthSnapshotRepository extends JpaRepository<MarketBreadthSnapshot, Long> {

    Optional<MarketBreadthSnapshot> findBySnapshotDate(LocalDate snapshotDate);

    Optional<MarketBreadthSnapshot> findTopByOrderBySnapshotDateDesc();

    List<MarketBreadthSnapshot> findTop60ByOrderBySnapshotDateDesc();
}
