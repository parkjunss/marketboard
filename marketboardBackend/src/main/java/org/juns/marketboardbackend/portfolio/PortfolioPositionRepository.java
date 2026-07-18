package org.juns.marketboardbackend.portfolio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    List<PortfolioPosition> findByPortfolio_IdOrderByIdAsc(Long portfolioId);

    Optional<PortfolioPosition> findByIdAndPortfolio_Id(Long id, Long portfolioId);

    boolean existsByPortfolio_IdAndSymbol_Id(Long portfolioId, Long symbolId);

    long countByPortfolio_Id(Long portfolioId);

    void deleteByPortfolio_Id(Long portfolioId);
}
