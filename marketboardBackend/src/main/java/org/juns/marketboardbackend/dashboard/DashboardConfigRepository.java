package org.juns.marketboardbackend.dashboard;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardConfigRepository extends JpaRepository<DashboardConfig, Long> {

    Optional<DashboardConfig> findByUser_Id(Long userId);
}
