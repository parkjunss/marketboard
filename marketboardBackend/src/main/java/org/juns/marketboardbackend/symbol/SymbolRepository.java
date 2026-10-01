package org.juns.marketboardbackend.symbol;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SymbolRepository extends JpaRepository<Symbol, Long> {

    Optional<Symbol> findByTickerIgnoreCase(String ticker);

    // Bulk lookup for QuoteService.resolvePrices() -- avoids one findByTickerIgnoreCase per
    // portfolio position that isn't in the live Redis quote set. Callers normalize to uppercase
    // themselves (same convention as findByTickerIgnoreCase's callers elsewhere) rather than
    // relying on IgnoreCase + In together, which isn't consistently supported across Spring Data
    // JPA versions for collection-valued parameters.
    List<Symbol> findByTickerIn(Collection<String> tickers);

    @Query(value = """
            SELECT s.* FROM symbols s
            LEFT JOIN stock_screening_snapshots ss
              ON ss.symbol_id=s.id AND ss.snapshot_run_id=(
                SELECT id FROM screening_snapshot_runs WHERE status='COMPLETED' ORDER BY id DESC LIMIT 1
              )
            WHERE s.is_active=TRUE
            ORDER BY CASE WHEN ss.market_cap IS NULL THEN 1 ELSE 0 END, ss.market_cap DESC, s.id
            """, nativeQuery = true)
    List<Symbol> findActiveOrderByLatestMarketCapDesc();

    @Query(value = """
            SELECT s.* FROM symbols s
            LEFT JOIN stock_screening_snapshots ss
              ON ss.symbol_id=s.id AND ss.snapshot_run_id=(
                SELECT id FROM screening_snapshot_runs WHERE status='COMPLETED' ORDER BY id DESC LIMIT 1
              )
            ORDER BY s.is_active DESC, CASE WHEN ss.market_cap IS NULL THEN 1 ELSE 0 END, ss.market_cap DESC, s.id
            """, nativeQuery = true)
    List<Symbol> findAllOrderByRealtimeThenLatestMarketCapDesc();

    List<Symbol> findByActiveTrueOrInSp500UniverseTrueOrderByPriorityAsc();
}
