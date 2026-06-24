package com.huanjing.geo.common.llm.measurement;

public interface LlmStructuredException {
    Integer httpStatusCode();

    String providerErrorCode();

    Long retryAfterMs();

    LlmErrorCategory errorCategory();
}
