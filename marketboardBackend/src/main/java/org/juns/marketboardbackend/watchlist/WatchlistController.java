package org.juns.marketboardbackend.watchlist;

import jakarta.validation.Valid;
import java.util.List;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.juns.marketboardbackend.watchlist.dto.WatchlistItemRequest;
import org.juns.marketboardbackend.watchlist.dto.WatchlistItemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistItemResponse> getWatchlist(@AuthenticationPrincipal AuthenticatedUser principal) {
        return watchlistService.getWatchlist(principal.id());
    }

    @PostMapping
    public ResponseEntity<WatchlistItemResponse> addItem(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody WatchlistItemRequest request) {
        WatchlistItemResponse response = watchlistService.addItem(principal.id(), request.ticker());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeItem(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        watchlistService.removeItem(principal.id(), id);
        return ResponseEntity.noContent().build();
    }
}
