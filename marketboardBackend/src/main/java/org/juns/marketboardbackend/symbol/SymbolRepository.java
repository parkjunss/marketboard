package org.juns.marketboardbackend.symbol;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SymbolRepository extends JpaRepository<Symbol, Long> {

    Optional<Symbol> findByTickerIgnoreCase(String ticker);

    List<Symbol> findByActiveTrueOrderByPriorityAsc();

    List<Symbol> findByActiveTrueOrInSp500UniverseTrueOrderByPriorityAsc();
}
