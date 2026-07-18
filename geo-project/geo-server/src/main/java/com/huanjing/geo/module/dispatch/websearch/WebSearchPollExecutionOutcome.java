package com.huanjing.geo.module.dispatch.websearch;

import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.enums.ResultCode;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;

public record WebSearchPollExecutionOutcome(boolean success,
                                            WebSearchResponse response,
                                            ResultCode resultCode,
                                            int attemptCount,
                                            long latencyMs,
                                            ErrorCategory errorCategory,
                                            String errorMessage) {

    public static WebSearchPollExecutionOutcome succeeded(WebSearchResponse response,
                                                          ResultCode resultCode,
                                                          int attemptCount,
                                                          long latencyMs) {
        return new WebSearchPollExecutionOutcome(
                true, response, resultCode, attemptCount, latencyMs, null, null);
    }

    public static WebSearchPollExecutionOutcome failed(int attemptCount,
                                                       long latencyMs,
                                                       ErrorCategory category,
                                                       String message) {
        return new WebSearchPollExecutionOutcome(
                false, null, ResultCode.R0, attemptCount, latencyMs, category, message);
    }
}
