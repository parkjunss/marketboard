package org.juns.marketboardbackend.watchlist.dto;

import org.juns.marketboardbackend.watchlist.WatchlistItem;

public record WatchlistItemResponse(Long id, Long symbolId, String ticker, String name, int sortOrder) {

    public static WatchlistItemResponse from(WatchlistItem item) {
        return new WatchlistItemResponse(
                item.getId(),
                item.getSymbol().getId(),
                item.getSymbol().getTicker(),
                item.getSymbol().getName(),
                item.getSortOrder());
    }
}
