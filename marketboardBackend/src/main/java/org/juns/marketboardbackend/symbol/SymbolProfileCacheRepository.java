package org.juns.marketboardbackend.symbol;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SymbolProfileCacheRepository extends JpaRepository<SymbolProfileCache, Long> {

    Optional<SymbolProfileCache> findByTickerIgnoreCase(String ticker);
}
