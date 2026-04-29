package com.huanjing.geo.module.content.service.adapter;

import lombok.Data;

@Data
public class SubmitResult {
    private boolean success;
    private Integer statusCode;
    private String requestPayload;
    private String responseBody;
    private String errorMessage;
    private String publishedUrl;
    private String platformArticleId;
    private String failureKind;
    private boolean retryable;

    public static SubmitResult success(Integer statusCode, String requestPayload, String responseBody, String publishedUrl) {
        SubmitResult result = new SubmitResult();
        result.success = true;
        result.statusCode = statusCode;
        result.requestPayload = requestPayload;
        result.responseBody = responseBody;
        result.publishedUrl = publishedUrl;
        return result;
    }

    public static SubmitResult success(Integer statusCode,
                                       String requestPayload,
                                       String responseBody,
                                       String publishedUrl,
                                       String platformArticleId) {
        SubmitResult result = success(statusCode, requestPayload, responseBody, publishedUrl);
        result.platformArticleId = platformArticleId;
        return result;
    }

    public static SubmitResult fail(Integer statusCode, String requestPayload, String responseBody, String errorMessage) {
        SubmitResult result = new SubmitResult();
        result.success = false;
        result.statusCode = statusCode;
        result.requestPayload = requestPayload;
        result.responseBody = responseBody;
        result.errorMessage = errorMessage;
        return result;
    }

    public static SubmitResult failure(Integer statusCode,
                                       String requestPayload,
                                       String responseBody,
                                       String errorMessage,
                                       String failureKind,
                                       boolean retryable) {
        SubmitResult result = fail(statusCode, requestPayload, responseBody, errorMessage);
        result.failureKind = failureKind;
        result.retryable = retryable;
        return result;
    }
}
