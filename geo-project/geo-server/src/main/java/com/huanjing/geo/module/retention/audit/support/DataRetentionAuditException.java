package com.huanjing.geo.module.retention.audit.support;

public class DataRetentionAuditException extends RuntimeException {

    private final String errorCode;

    public DataRetentionAuditException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DataRetentionAuditException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
