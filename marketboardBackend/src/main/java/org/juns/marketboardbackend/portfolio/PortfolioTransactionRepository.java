package org.juns.marketboardbackend.portfolio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {

    @EntityGraph(attributePaths = "symbol")
    List<PortfolioTransaction> findByPortfolio_IdOrderByOccurredAtAscIdAsc(Long portfolioId);

    Optional<PortfolioTransaction> findFirstByPortfolio_IdOrderByOccurredAtDescIdDesc(Long portfolioId);

    Optional<PortfolioTransaction> findByIdAndPortfolio_Id(Long id, Long portfolioId);

    boolean existsByPortfolio_Id(Long portfolioId);

    boolean existsByReversalOf_Id(Long transactionId);

    void deleteByPortfolio_Id(Long portfolioId);

    @EntityGraph(attributePaths = {"portfolio", "symbol"})
    List<PortfolioTransaction> findByTransactionTypeOrderBySourcePositionIdAsc(
            PortfolioTransactionType transactionType);
}
