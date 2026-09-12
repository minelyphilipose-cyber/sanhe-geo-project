package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.llm.measurement.LlmStructuredException;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteFailureKind;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.regex.Pattern;

final class ArticleGenerationFailureClassifier {
    private static final Pattern HTTP_SERVER_ERROR = Pattern.compile("\\bhttp\\s+5\\d{2}\\b");
    private static final Pattern HTTP_CLIENT_ERROR = Pattern.compile("\\bhttp\\s+4\\d{2}\\b");

    private ArticleGenerationFailureClassifier() {
    }

    static Classification classify(Throwable error) {
        if (error == null) {
            return Classification.nonRetryable();
        }
        String message = messageChain(error);
        if (isExplicitNonRetryableFailure(message) || hasNonRetryableNetworkCause(error)) {
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
                case ALL_CIRCUIT_OPEN -> {
                    return Classification.providerRetryable(null);
                }
                case NO_CANDIDATE, INTERRUPTED -> {
                    return Classification.nonRetryable();
                }
                case ALL_FAILED -> {
                    // Inspect the wrapped provider failure below. ALL_FAILED can wrap a
                    // permanent authentication, content-policy or parameter rejection.
                }
            }
        }
        LlmStructuredException structured = findStructured(error);
        if (structured != null && structured.errorCategory() != null) {
            switch (structured.errorCategory()) {
                case PERMIT_BUSY, INTERNAL_RATE_LIMITED -> {
                    return Classification.capacity();
                }
                case PLATFORM_429, HTTP_5XX, TIMEOUT, EMPTY_RESPONSE -> {
                    return Classification.providerRetryable(structured.retryAfterMs());
                }
                case ACCOUNT_QUOTA_EXHAUSTED, AUTHENTICATION_FAILED,
                     CONFIG_ERROR, BUSINESS_NON_RETRYABLE -> {
                    return Classification.nonRetryable();
                }
                case INVOKE_FAILED -> {
                    // The category alone does not say whether replay is safe. Inspect
                    // the actual provider/network cause before deciding.
                }
            }
        }
        if (isCapacityFailure(message)) {
            return Classification.capacity();
        }
        if (hasRetryableNetworkCause(error)) {
            return Classification.providerRetryable(null);
        }
        return isInfrastructureFailure(message)
                ? Classification.providerRetryable(null)
                : Classification.nonRetryable();
    }

    private static boolean isExplicitNonRetryableFailure(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return false;
        }
        String normalized = errorMessage.toLowerCase(Locale.ROOT);
        boolean retryableClientStatus = normalized.contains("http 408") || normalized.contains("http 429");
        return normalized.contains("inputtextsensitivecontentdetected")
                || normalized.contains("invalidparameter")
                || normalized.contains("invalid parameter")
                || normalized.contains("authentication failed")
                || normalized.contains("unauthorized")
                || normalized.contains("forbidden")
                || normalized.contains("invalid api key")
                || normalized.contains("account quota")
                || (!retryableClientStatus && HTTP_CLIENT_ERROR.matcher(normalized).find());
    }

    private static boolean hasRetryableNetworkCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof ConnectException
                    || current instanceof SocketException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasNonRetryableNetworkCause(Throwable error) {
        return findCause(error, SSLException.class) != null
                || findCause(error, UnknownHostException.class) != null;
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
                || normalized.contains("http 408")
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
