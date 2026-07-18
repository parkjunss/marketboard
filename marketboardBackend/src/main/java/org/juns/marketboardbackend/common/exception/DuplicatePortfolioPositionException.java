package org.juns.marketboardbackend.common.exception;

public class DuplicatePortfolioPositionException extends RuntimeException {

    public DuplicatePortfolioPositionException(String ticker) {
        super("Symbol already in this portfolio: " + ticker);
    }
}
