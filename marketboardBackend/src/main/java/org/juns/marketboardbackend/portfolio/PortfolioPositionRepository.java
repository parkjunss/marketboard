package org.juns.marketboardbackend.portfolio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    @EntityGraph(attributePaths = "symbol")
    List<PortfolioPosition> findByPortfolio_IdOrderByIdAsc(Long portfolioId);

    @Query("""
            select p from PortfolioPosition p
            join fetch p.portfolio
            join fetch p.symbol
            where p.portfolio.user.id = :userId
            order by p.portfolio.id, p.id
            """)
    List<PortfolioPosition> findAllByUserId(@Param("userId") Long userId);

    Optional<PortfolioPosition> findByIdAndPortfolio_Id(Long id, Long portfolioId);

    Optional<PortfolioPosition> findByPortfolio_IdAndSymbol_Id(Long portfolioId, Long symbolId);

    boolean existsByPortfolio_IdAndSymbol_Id(Long portfolioId, Long symbolId);

    long countByPortfolio_Id(Long portfolioId);

    void deleteByPortfolio_Id(Long portfolioId);

    void deleteBySymbol_Id(Long symbolId);
}
