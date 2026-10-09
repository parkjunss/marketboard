package org.juns.marketboardbackend.portfolio;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PortfolioOpeningBalanceBackfillRepository {

    private final EntityManager entityManager;

    public PortfolioOpeningBalanceBackfillRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<PortfolioPosition> findAllPositionsForUpdate() {
        return entityManager.createQuery("""
                        select p from PortfolioPosition p
                        join fetch p.portfolio
                        join fetch p.symbol
                        order by p.id
                        """, PortfolioPosition.class)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }
}
