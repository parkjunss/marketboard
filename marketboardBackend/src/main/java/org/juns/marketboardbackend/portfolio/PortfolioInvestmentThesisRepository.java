package org.juns.marketboardbackend.portfolio;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioInvestmentThesisRepository extends JpaRepository<PortfolioInvestmentThesis, Long> {

    @EntityGraph(attributePaths = "symbol")
    List<PortfolioInvestmentThesis> findByPortfolio_IdOrderBySymbol_IdAscRevisionDesc(Long portfolioId);
}
