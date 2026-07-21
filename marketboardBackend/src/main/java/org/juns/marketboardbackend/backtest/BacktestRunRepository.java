package org.juns.marketboardbackend.backtest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long> {

    List<BacktestRun> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<BacktestRun> findByIdAndUser_Id(Long id, Long userId);
}
