package org.juns.marketboardbackend.common.exception;

public class AccountSuspendedException extends RuntimeException {

    public AccountSuspendedException() {
        super("Account is suspended");
    }
}
