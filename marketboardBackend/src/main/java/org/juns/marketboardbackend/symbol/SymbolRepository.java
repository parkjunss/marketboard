package org.juns.marketboardbackend.symbol;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SymbolRepository extends JpaRepository<Symbol, Long> {

    Optional<Symbol> findByTickerIgnoreCase(String ticker);

    // Bulk lookup for QuoteService.resolvePrices() -- avoids one findByTickerIgnoreCase per
    // portfolio position that isn't in the live Redis quote set. Callers normalize to uppercase
    // themselves (same convention as findByTickerIgnoreCase's callers elsewhere) rather than
    // relying on IgnoreCase + In together, which isn't consistently supported across Spring Data
    // JPA versions for collection-valued parameters.
    List<Symbol> findByTickerIn(Collection<String> tickers);

    List<Symbol> findByActiveTrueOrderByPriorityAsc();

    List<Symbol> findByActiveTrueOrInSp500UniverseTrueOrderByPriorityAsc();
}
