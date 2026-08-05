package com.huanjing.geo.module.dispatch.service;

public class PollResultPersistenceBusyException extends RuntimeException {

    public PollResultPersistenceBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
