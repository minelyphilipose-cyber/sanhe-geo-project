package com.huanjing.geo.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;
    private final int httpStatus;
    private final Object data;

    public BizException(String message) {
        super(message);
        this.code = -1;
        this.httpStatus = 200;
        this.data = null;
    }

    public BizException(int code, String message) {
        super(message);
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
}
