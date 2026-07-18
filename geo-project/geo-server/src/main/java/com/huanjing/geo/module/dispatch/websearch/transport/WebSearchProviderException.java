package com.huanjing.geo.module.dispatch.websearch.transport;

import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;

public class WebSearchProviderException extends RuntimeException {
    private final ErrorCategory category;
    private final Integer httpStatus;
    private final boolean retryable;

    public WebSearchProviderException(ErrorCategory category,
                                      Integer httpStatus,
                                      String message,
                                      Throwable cause) {
        super(message, cause);
        this.category = category;
        this.httpStatus = httpStatus;
        this.retryable = category.retryable();
    }

    public ErrorCategory category() {
        return category;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }
}
