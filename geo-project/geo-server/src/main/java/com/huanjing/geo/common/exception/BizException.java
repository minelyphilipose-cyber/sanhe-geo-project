package com.huanjing.geo.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    /**
     * Generic business error code used by legacy call sites that only provide a message.
     */
    public static final int DEFAULT_CODE = -1;

    private final int code;
    private final int httpStatus;
    private final Object data;

    public BizException(String message) {
        super(message);
        this.code = DEFAULT_CODE;
        this.httpStatus = 200;
        this.data = null;
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
        this.code = DEFAULT_CODE;
        this.httpStatus = 200;
        this.data = null;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = 200;
        this.data = null;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = 200;
        this.data = null;
    }

    public BizException(int code, String message, int httpStatus, Object data) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.data = data;
    }

    public BizException(int code, String message, int httpStatus, Object data, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.data = data;
    }
}
