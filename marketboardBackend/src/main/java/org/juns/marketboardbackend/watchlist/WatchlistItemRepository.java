package org.juns.marketboardbackend.watchlist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByUser_IdOrderBySortOrderAsc(Long userId);

    boolean existsByUser_IdAndSymbol_Id(Long userId, Long symbolId);

    Optional<WatchlistItem> findByIdAndUser_Id(Long id, Long userId);

    int countByUser_Id(Long userId);

    void deleteBySymbol_Id(Long symbolId);

    void deleteByUser_Id(Long userId);
}
