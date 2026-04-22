package com.huanjing.geo.module.presale.generate.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmCallResultTest {

    @Test
    void isRetriedSuccess_trueWhenSuccessAndRetryCountPositive() {
        LlmCallResult result = new LlmCallResult(
                "ok", 10, 20, 1200L, 1, CallStatus.SUCCESS
        );

        assertTrue(result.isRetriedSuccess());
    }

    @Test
    void isRetriedSuccess_falseWhenSuccessWithoutRetry() {
        LlmCallResult result = new LlmCallResult(
                "ok", 10, 20, 1200L, 0, CallStatus.SUCCESS
        );

        assertFalse(result.isRetriedSuccess());
    }

    @Test
    void isRetriedSuccess_falseWhenFailedEvenWithRetry() {
        LlmCallResult result = new LlmCallResult(
                null, null, null, 500L, 2, CallStatus.FAILED
        );

        assertFalse(result.isRetriedSuccess());
    }
}

