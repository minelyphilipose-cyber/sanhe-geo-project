package com.huanjing.geo.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(String message) {
        super(message);
        this.code = -1;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
