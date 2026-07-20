package org.juns.marketboardbackend.pricehistory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    List<PriceHistory> findBySymbol_TickerIgnoreCaseAndTimeframeOrderByTsDesc(
            String ticker, String timeframe, Pageable pageable);

    Optional<PriceHistory> findFirstBySymbol_TickerIgnoreCaseAndTimeframeOrderByTsDesc(String ticker, String timeframe);

    void deleteBySymbol_Id(Long symbolId);
}
