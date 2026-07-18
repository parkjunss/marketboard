package org.juns.marketboardbackend.common.exception;

public class DuplicateWatchlistItemException extends RuntimeException {

    public DuplicateWatchlistItemException(String ticker) {
        super("Symbol already in watchlist: " + ticker);
    }
}
