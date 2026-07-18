package org.juns.marketboardbackend.portfolio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByUser_IdOrderByCreatedAtAsc(Long userId);

    Optional<Portfolio> findByIdAndUser_Id(Long id, Long userId);
}
