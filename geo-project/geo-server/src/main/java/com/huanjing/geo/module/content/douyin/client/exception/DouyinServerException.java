package com.huanjing.geo.module.content.douyin.client.exception;

public class DouyinServerException extends DouyinClientException {
    public DouyinServerException(int httpStatus,
                                 Long errorCode,
                                 String description,
                                 String logId,
                                 boolean retryable,
                                 String rawBody) {
        super(httpStatus, errorCode, description, logId, retryable, rawBody);
    }
}
