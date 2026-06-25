package com.huanjing.geo.common.llm.capacity;

import com.huanjing.geo.common.llm.measurement.LlmErrorCategory;
import com.huanjing.geo.common.llm.measurement.LlmHttpErrorException;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteFailureKind;
import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmCapacityFailureClassifierTest {
    private final LlmCapacityFailureClassifier classifier = new LlmCapacityFailureClassifier();

    @Test
    void extractsRetryAfterFromStructuredPlatform429() {
        LlmRouteException error = new LlmRouteException(
                LlmRouteFailureKind.ALL_FAILED,
                "all failed",
                1,
                new LlmHttpErrorException(
                        429,
                        "rate limited",
                        "rate_limit_exceeded",
                        12_000L,
                        LlmErrorCategory.PLATFORM_429
                )
        );

        Optional<LlmCapacityFailure> result = classifier.classify(error);

        assertTrue(result.isPresent());
        assertEquals(LlmErrorCategory.PLATFORM_429, result.get().errorCategory());
        assertEquals(12_000L, result.get().retryAfterMs());
    }

    @Test
    void classifiesRoutePermitBusyAsCapacityFailure() {
        LlmRouteException error = new LlmRouteException(
                LlmRouteFailureKind.ALL_PERMIT_BUSY,
                "busy",
                0,
                null
        );

        Optional<LlmCapacityFailure> result = classifier.classify(error);

        assertTrue(result.isPresent());
        assertEquals(LlmErrorCategory.PERMIT_BUSY, result.get().errorCategory());
    }

    @Test
    void detectsTimeoutInCauseChain() {
        RuntimeException error = new RuntimeException("wrapper", new HttpTimeoutException("request timed out"));

        Optional<LlmCapacityFailure> result = classifier.classify(error);

        assertTrue(result.isPresent());
        assertEquals(LlmErrorCategory.TIMEOUT, result.get().errorCategory());
    }
}
