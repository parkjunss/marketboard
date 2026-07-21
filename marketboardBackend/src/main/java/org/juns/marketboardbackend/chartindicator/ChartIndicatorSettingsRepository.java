package org.juns.marketboardbackend.chartindicator;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartIndicatorSettingsRepository extends JpaRepository<ChartIndicatorSettings, Long> {

    Optional<ChartIndicatorSettings> findByUser_Id(Long userId);
}
