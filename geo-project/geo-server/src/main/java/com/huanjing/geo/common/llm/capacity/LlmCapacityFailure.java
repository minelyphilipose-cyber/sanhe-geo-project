package com.huanjing.geo.common.llm.capacity;

import com.huanjing.geo.common.llm.measurement.LlmErrorCategory;

public record LlmCapacityFailure(
        LlmErrorCategory errorCategory,
        Long retryAfterMs,
        String reason,
        String source
) {
    public boolean hasRetryAfter() {
        return retryAfterMs != null && retryAfterMs > 0L;
    }
}
