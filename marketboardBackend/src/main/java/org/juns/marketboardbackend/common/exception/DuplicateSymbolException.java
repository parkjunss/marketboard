package org.juns.marketboardbackend.common.exception;

public class DuplicateSymbolException extends RuntimeException {

    public DuplicateSymbolException(String ticker) {
        super("Symbol already exists: " + ticker);
    }
}
