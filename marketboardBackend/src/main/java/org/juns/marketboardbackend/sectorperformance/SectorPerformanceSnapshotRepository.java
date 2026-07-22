package org.juns.marketboardbackend.sectorperformance;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorPerformanceSnapshotRepository extends JpaRepository<SectorPerformanceSnapshot, Long> {

    // Always at most one row in practice -- refresh() finds-and-updates it rather than inserting a
    // new one each time -- but ordering by id descending is a cheap safety net if that ever changes.
    Optional<SectorPerformanceSnapshot> findTopByOrderByIdDesc();
}
