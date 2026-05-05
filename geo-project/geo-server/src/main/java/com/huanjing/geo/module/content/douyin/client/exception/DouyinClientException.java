package com.huanjing.geo.module.content.douyin.client.exception;

import lombok.Getter;

@Getter
public class DouyinClientException extends RuntimeException {
    private final int httpStatus;
    private final Long errorCode;
    private final String description;
    private final String logId;
    private final boolean retryable;
    private final String rawBody;

    public DouyinClientException(int httpStatus,
                                 Long errorCode,
                                 String description,
                                 String logId,
                                 boolean retryable,
                                 String rawBody) {
        super(description == null || description.isBlank() ? "douyin client request failed" : description);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.description = description;
        this.logId = logId;
        this.retryable = retryable;
        this.rawBody = rawBody;
    }
}
