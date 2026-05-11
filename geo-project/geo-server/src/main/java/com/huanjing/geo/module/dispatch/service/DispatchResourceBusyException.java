package com.huanjing.geo.module.dispatch.service;

public class DispatchResourceBusyException extends RuntimeException {
    public DispatchResourceBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
