package org.juns.marketboardbackend.sentiment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PutCallRatioSnapshotRepository extends JpaRepository<PutCallRatioSnapshot, Long> {

    Optional<PutCallRatioSnapshot> findByTickerIgnoreCase(String ticker);
}
