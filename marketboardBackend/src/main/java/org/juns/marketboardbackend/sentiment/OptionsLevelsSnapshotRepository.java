package org.juns.marketboardbackend.sentiment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionsLevelsSnapshotRepository extends JpaRepository<OptionsLevelsSnapshot, Long> {

    Optional<OptionsLevelsSnapshot> findByTickerIgnoreCase(String ticker);
}
