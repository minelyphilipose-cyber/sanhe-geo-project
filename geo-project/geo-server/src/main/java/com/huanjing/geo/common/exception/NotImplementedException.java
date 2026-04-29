package com.huanjing.geo.common.exception;

/**
 * Thrown when a code path is not yet implemented (e.g. Phase 0 {@code distributeTo} placeholder).
 */
public class NotImplementedException extends RuntimeException {

    public NotImplementedException(String message) {
        super(message);
    }
}
