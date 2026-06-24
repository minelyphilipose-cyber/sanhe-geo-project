package com.huanjing.geo.common.llm.measurement;

import com.huanjing.geo.common.llm.LlmInvokeException;

public class LlmHttpErrorException extends LlmInvokeException implements LlmStructuredException {
    private final Integer httpStatusCode;
    private final String providerErrorCode;
    private final Long retryAfterMs;
    private final LlmErrorCategory errorCategory;

    public LlmHttpErrorException(Integer httpStatusCode,
                                 String message,
                                 String providerErrorCode,
                                 Long retryAfterMs,
                                 LlmErrorCategory errorCategory) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.providerErrorCode = providerErrorCode;
        this.retryAfterMs = retryAfterMs;
        this.errorCategory = errorCategory;
    }

    @Override
    public Integer httpStatusCode() {
        return httpStatusCode;
    }

    @Override
    public String providerErrorCode() {
        return providerErrorCode;
    }

    @Override
    public Long retryAfterMs() {
        return retryAfterMs;
    }

    @Override
    public LlmErrorCategory errorCategory() {
        return errorCategory;
    }
}
