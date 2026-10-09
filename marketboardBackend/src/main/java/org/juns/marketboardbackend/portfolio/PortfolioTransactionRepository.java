package org.juns.marketboardbackend.portfolio;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {

    interface LedgerBasisProjection {
        Long getPortfolioId();
        long getTransactionCount();
        Long getLastTransactionId();
        Instant getLastOccurredAt();
    }

    @EntityGraph(attributePaths = "symbol")
    List<PortfolioTransaction> findByPortfolio_IdOrderByOccurredAtAscIdAsc(Long portfolioId);

    Optional<PortfolioTransaction> findFirstByPortfolio_IdOrderByOccurredAtDescIdDesc(Long portfolioId);

    Optional<PortfolioTransaction> findByIdAndPortfolio_Id(Long id, Long portfolioId);

    boolean existsByPortfolio_Id(Long portfolioId);

    boolean existsByReversalOf_Id(Long transactionId);

    void deleteByPortfolio_Id(Long portfolioId);

    @Query("""
            select t.portfolio.id as portfolioId, count(t.id) as transactionCount,
                   max(t.id) as lastTransactionId, max(t.occurredAt) as lastOccurredAt
            from PortfolioTransaction t
            where t.portfolio.user.id = :userId
            group by t.portfolio.id
            """)
    List<LedgerBasisProjection> findLedgerBasisByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"portfolio", "symbol"})
    List<PortfolioTransaction> findByTransactionTypeOrderBySourcePositionIdAsc(
            PortfolioTransactionType transactionType);
}
