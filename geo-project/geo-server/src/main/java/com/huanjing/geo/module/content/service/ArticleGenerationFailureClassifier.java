package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.llm.measurement.LlmStructuredException;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteFailureKind;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

final class ArticleGenerationFailureClassifier {
    private static final Pattern HTTP_SERVER_ERROR = Pattern.compile("\\bhttp\\s+5\\d{2}\\b");

    private ArticleGenerationFailureClassifier() {
    }

    static Classification classify(Throwable error) {
        if (error == null) {
            return Classification.nonRetryable();
        }
        LlmPermitUnavailableException permitUnavailable = findCause(error, LlmPermitUnavailableException.class);
        if (permitUnavailable != null) {
            return Classification.capacity();
        }
        LlmRouteException routeException = findCause(error, LlmRouteException.class);
        if (routeException != null && routeException.failureKind() != null) {
            switch (routeException.failureKind()) {
                case ALL_PERMIT_BUSY, ALL_RATE_LIMITED -> {
                    return Classification.capacity();
                }
                case ALL_CIRCUIT_OPEN, ALL_FAILED -> {
                    return Classification.providerRetryable(null);
                }
                case NO_CANDIDATE, INTERRUPTED -> {
                    return Classification.nonRetryable();
                }
            }
        }
        LlmStructuredException structured = findStructured(error);
        if (structured != null && structured.errorCategory() != null) {
            return switch (structured.errorCategory()) {
                case PERMIT_BUSY, INTERNAL_RATE_LIMITED -> Classification.capacity();
                case PLATFORM_429, HTTP_5XX, TIMEOUT, INVOKE_FAILED ->
                        Classification.providerRetryable(structured.retryAfterMs());
                case CONFIG_ERROR, BUSINESS_NON_RETRYABLE -> Classification.nonRetryable();
            };
        }
        String message = messageChain(error);
        if (isCapacityFailure(message)) {
            return Classification.capacity();
        }
        return isInfrastructureFailure(message)
                ? Classification.providerRetryable(null)
                : Classification.nonRetryable();
    }

    static boolean isInfrastructureFailure(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return false;
        }
        String normalized = errorMessage.toLowerCase(Locale.ROOT);
        if (isCapacityFailure(normalized)) {
            return false;
        }
        return normalized.contains("timed out")
                || normalized.contains("timeout")
                || normalized.contains("http 429")
                || normalized.contains("too many requests")
                || normalized.contains("rate limit")
                || normalized.contains("connection refused")
                || normalized.contains("connection reset")
                || normalized.contains("connection closed")
                || normalized.contains("circuit-open")
                || normalized.contains("circuit open")
                || normalized.contains("connectexception")
                || normalized.contains("socketexception")
                || HTTP_SERVER_ERROR.matcher(normalized).find();
    }

    static boolean isCapacityFailure(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return false;
        }
        String normalized = errorMessage.toLowerCase(Locale.ROOT);
        return normalized.contains("permit unavailable")
                || normalized.contains("permit busy")
                || normalized.contains("all llm candidates are waiting for permits")
                || normalized.contains("internal rate limit")
                || normalized.contains("all llm candidates are rate limited");
    }

    private static LlmStructuredException findStructured(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof LlmStructuredException structured) {
                return structured;
            }
            current = current.getCause();
        }
        return null;
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static String messageChain(Throwable error) {
        StringBuilder value = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                if (!value.isEmpty()) {
                    value.append(" | ");
                }
                value.append(current.getMessage());
            }
            current = current.getCause();
        }
        return value.toString();
    }

    enum FailureKind {
        PROVIDER_RETRYABLE,
        CAPACITY_DEFERRED,
        NON_RETRYABLE
    }

    record Classification(FailureKind kind, Long retryAfterMs) {
        static Classification providerRetryable(Long retryAfterMs) {
            return new Classification(FailureKind.PROVIDER_RETRYABLE, retryAfterMs);
        }

        static Classification capacity() {
            return new Classification(FailureKind.CAPACITY_DEFERRED, null);
        }

        static Classification nonRetryable() {
            return new Classification(FailureKind.NON_RETRYABLE, null);
        }

        boolean providerRetryable() {
            return kind == FailureKind.PROVIDER_RETRYABLE;
        }

        boolean capacityDeferred() {
            return kind == FailureKind.CAPACITY_DEFERRED;
        }
    }
}
