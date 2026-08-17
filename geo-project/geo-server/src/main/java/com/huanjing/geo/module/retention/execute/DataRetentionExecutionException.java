package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.common.exception.BizException;

public class DataRetentionExecutionException extends BizException {
    private final String resultCode;

    public DataRetentionExecutionException(int status, String resultCode, String message) {
        super(status, message, status, resultCode);
        this.resultCode = resultCode;
    }

    public DataRetentionExecutionException(int status, String resultCode, String message, Throwable cause) {
        super(status, message, status, resultCode, cause);
        this.resultCode = resultCode;
    }

    public String resultCode() { return resultCode; }
}
