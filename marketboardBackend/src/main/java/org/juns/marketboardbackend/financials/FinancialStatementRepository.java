package org.juns.marketboardbackend.financials;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialStatementRepository extends JpaRepository<FinancialStatement, Long> {

    Optional<FinancialStatement> findByTickerIgnoreCase(String ticker);
}
