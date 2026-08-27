package com.huanjing.geo.common.llm.measurement;

import com.huanjing.geo.common.llm.LlmInvokeException;

public class LlmResponseException extends LlmInvokeException implements LlmStructuredException {
    private final String providerErrorCode;
    private final LlmErrorCategory errorCategory;

    public LlmResponseException(String message,
                                String providerErrorCode,
                                LlmErrorCategory errorCategory) {
        super(message);
        this.providerErrorCode = providerErrorCode;
        this.errorCategory = errorCategory;
    }

    @Override
    public Integer httpStatusCode() {
        return 200;
    }

    @Override
    public String providerErrorCode() {
        return providerErrorCode;
    }

    @Override
    public Long retryAfterMs() {
        return null;
    }

    @Override
    public LlmErrorCategory errorCategory() {
        return errorCategory;
    }
}
