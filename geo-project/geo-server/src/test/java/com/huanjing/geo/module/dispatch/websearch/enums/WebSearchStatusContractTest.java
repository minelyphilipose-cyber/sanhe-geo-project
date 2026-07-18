package com.huanjing.geo.module.dispatch.websearch.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchStatusContractTest {

    @Test
    void attemptStatusOnlyMovesForward() {
        assertTrue(AttemptStatus.PENDING.canTransitionTo(AttemptStatus.RUNNING));
        assertTrue(AttemptStatus.RUNNING.canTransitionTo(AttemptStatus.SUCCEEDED));
        assertTrue(AttemptStatus.RUNNING.canTransitionTo(AttemptStatus.FAILED));
        assertTrue(AttemptStatus.RUNNING.canTransitionTo(AttemptStatus.ABANDONED));
        assertFalse(AttemptStatus.SUCCEEDED.canTransitionTo(AttemptStatus.RUNNING));
        assertFalse(AttemptStatus.FAILED.canTransitionTo(AttemptStatus.RUNNING));
        assertFalse(AttemptStatus.ABANDONED.canTransitionTo(AttemptStatus.RUNNING));
    }

    @Test
    void providerCallStatusOnlyMovesForward() {
        assertTrue(ProviderCallStatus.PENDING.canTransitionTo(ProviderCallStatus.RUNNING));
        assertTrue(ProviderCallStatus.RUNNING.canTransitionTo(ProviderCallStatus.SUCCEEDED));
        assertTrue(ProviderCallStatus.RUNNING.canTransitionTo(ProviderCallStatus.FAILED));
        assertTrue(ProviderCallStatus.RUNNING.canTransitionTo(ProviderCallStatus.ABANDONED));
        assertFalse(ProviderCallStatus.SUCCEEDED.canTransitionTo(ProviderCallStatus.RUNNING));
    }

    @Test
    void emptySearchStillMeansSearchWasExecuted() {
        assertTrue(SearchStatus.EMPTY.searchActuallyExecuted());
        assertTrue(SearchStatus.NO_VALID_SOURCE.searchActuallyExecuted());
        assertFalse(SearchStatus.NOT_CONFIRMED.searchActuallyExecuted());
        assertFalse(SearchStatus.EMPTY.hasUsableSources());
    }

    @Test
    void onlyTransportAndProviderTransientErrorsAreRetryable() {
        assertTrue(ErrorCategory.TIMEOUT.retryable());
        assertTrue(ErrorCategory.RATE_LIMIT.retryable());
        assertFalse(ErrorCategory.AUTHENTICATION.retryable());
        assertFalse(ErrorCategory.SAFETY_REJECTION.retryable());
    }
}
