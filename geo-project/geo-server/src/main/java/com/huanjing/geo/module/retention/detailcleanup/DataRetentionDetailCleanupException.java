package com.huanjing.geo.module.retention.detailcleanup;

public class DataRetentionDetailCleanupException extends RuntimeException {
    private final String code;
    private final boolean invariant;

    public DataRetentionDetailCleanupException(String code, String message) {
        this(code, message, false, null);
    }

    public DataRetentionDetailCleanupException(String code, String message, boolean invariant) {
        this(code, message, invariant, null);
    }

    public DataRetentionDetailCleanupException(String code, String message, boolean invariant, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.invariant = invariant;
    }

    public String code() { return code; }
    public boolean invariant() { return invariant; }
}
