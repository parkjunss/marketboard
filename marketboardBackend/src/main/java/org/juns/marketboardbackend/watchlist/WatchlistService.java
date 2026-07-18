package org.juns.marketboardbackend.watchlist;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.juns.marketboardbackend.common.exception.DuplicateWatchlistItemException;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.juns.marketboardbackend.watchlist.dto.WatchlistItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchlistService {

    private final WatchlistItemRepository watchlistItemRepository;
    private final SymbolRepository symbolRepository;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;

    public WatchlistService(
            WatchlistItemRepository watchlistItemRepository,
            SymbolRepository symbolRepository,
            UserRepository userRepository,
            MeterRegistry meterRegistry) {
        this.watchlistItemRepository = watchlistItemRepository;
        this.symbolRepository = symbolRepository;
        this.userRepository = userRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> getWatchlist(Long userId) {
        return watchlistItemRepository.findByUser_IdOrderBySortOrderAsc(userId).stream()
                .map(WatchlistItemResponse::from)
                .toList();
    }

    @Transactional
    public WatchlistItemResponse addItem(Long userId, String ticker) {
        Symbol symbol = symbolRepository.findByTickerIgnoreCase(ticker)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown symbol " + ticker.toUpperCase()));
        if (watchlistItemRepository.existsByUser_IdAndSymbol_Id(userId, symbol.getId())) {
            throw new DuplicateWatchlistItemException(symbol.getTicker());
        }
        User user = userRepository.getReferenceById(userId);
        int nextOrder = watchlistItemRepository.countByUser_Id(userId);
        WatchlistItem saved = watchlistItemRepository.save(
                WatchlistItem.builder().user(user).symbol(symbol).sortOrder(nextOrder).build());
        meterRegistry.counter("marketboard.watchlist.items.created").increment();
        return WatchlistItemResponse.from(saved);
    }

    @Transactional
    public void removeItem(Long userId, Long itemId) {
        WatchlistItem item = watchlistItemRepository.findByIdAndUser_Id(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist item not found: " + itemId));
        watchlistItemRepository.delete(item);
    }
}
