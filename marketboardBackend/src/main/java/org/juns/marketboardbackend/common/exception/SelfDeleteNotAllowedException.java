package org.juns.marketboardbackend.common.exception;

public class SelfDeleteNotAllowedException extends RuntimeException {

    public SelfDeleteNotAllowedException() {
        super("Cannot delete your own account");
    }
}
