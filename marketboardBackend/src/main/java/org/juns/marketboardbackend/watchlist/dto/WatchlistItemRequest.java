package org.juns.marketboardbackend.watchlist.dto;

import jakarta.validation.constraints.NotBlank;

public record WatchlistItemRequest(@NotBlank String ticker) {
}
