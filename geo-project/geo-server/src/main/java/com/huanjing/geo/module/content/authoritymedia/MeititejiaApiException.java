package com.huanjing.geo.module.content.authoritymedia;

public class MeititejiaApiException extends RuntimeException {
    private final int httpStatus;
    private final Integer bizCode;
    private final String bizMsg;
    private final String requestPath;
    private final String responseBody;
    private final boolean retryable;

    public MeititejiaApiException(int httpStatus,
                                  Integer bizCode,
                                  String bizMsg,
                                  String requestPath,
                                  String responseBody,
                                  boolean retryable,
                                  Throwable cause) {
        super(buildMessage(httpStatus, bizCode, bizMsg, requestPath), cause);
        this.httpStatus = httpStatus;
        this.bizCode = bizCode;
        this.bizMsg = bizMsg;
        this.requestPath = requestPath;
        this.responseBody = responseBody;
        this.retryable = retryable;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Integer getBizCode() {
        return bizCode;
    }

    public String getBizMsg() {
        return bizMsg;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isRetryable() {
        return retryable;
    }

    private static String buildMessage(int httpStatus, Integer bizCode, String bizMsg, String requestPath) {
        StringBuilder message = new StringBuilder("Meititejia request failed");
        if (requestPath != null) {
            message.append(" path=").append(requestPath);
        }
        message.append(" httpStatus=").append(httpStatus);
        if (bizCode != null) {
            message.append(" bizCode=").append(bizCode);
        }
        if (bizMsg != null && !bizMsg.isBlank()) {
            message.append(" bizMsg=").append(bizMsg);
        }
        return message.toString();
    }
}
