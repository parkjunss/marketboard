package org.juns.marketboardbackend.portfolio;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioWeightRuleRepository extends JpaRepository<PortfolioWeightRule, Long> {
    Optional<PortfolioWeightRule> findByPortfolioId(Long portfolioId);
}
