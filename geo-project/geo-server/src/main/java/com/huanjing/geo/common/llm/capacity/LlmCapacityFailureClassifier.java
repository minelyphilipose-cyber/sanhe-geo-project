package com.huanjing.geo.common.llm.capacity;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.measurement.LlmErrorCategory;
import com.huanjing.geo.common.llm.measurement.LlmStructuredException;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteFailureKind;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Service
public class LlmCapacityFailureClassifier {

    public Optional<LlmCapacityFailure> classify(Throwable error) {
        if (error == null) {
            return Optional.empty();
        }
        LlmStructuredException structured = find(error, LlmStructuredException.class);
        if (structured != null) {
            Optional<LlmCapacityFailure> classified = classifyStructured(structured);
            if (classified.isPresent()) {
                return classified;
            }
        }

        LlmPermitUnavailableException permitUnavailable = find(error, LlmPermitUnavailableException.class);
        if (permitUnavailable != null) {
            return Optional.of(new LlmCapacityFailure(
                    LlmErrorCategory.PERMIT_BUSY,
                    null,
                    permitUnavailable.getMessage(),
                    permitUnavailable.getClass().getSimpleName()
            ));
        }

        LlmRouteException routeException = find(error, LlmRouteException.class);
        if (routeException != null) {
            Optional<LlmCapacityFailure> classified = classifyRoute(routeException);
            if (classified.isPresent()) {
                return classified;
            }
        }

        BizException bizException = find(error, BizException.class);
        if (bizException != null) {
            if (bizException.getCode() == 429) {
                return Optional.of(new LlmCapacityFailure(
                        LlmErrorCategory.INTERNAL_RATE_LIMITED,
                        null,
                        bizException.getMessage(),
                        bizException.getClass().getSimpleName()
                ));
            }
            if (bizException.getCode() >= 500 && bizException.getCode() < 600) {
                return Optional.of(new LlmCapacityFailure(
                        LlmErrorCategory.HTTP_5XX,
                        null,
                        bizException.getMessage(),
                        bizException.getClass().getSimpleName()
                ));
            }
        }

        if (containsTimeout(error)) {
            return Optional.of(new LlmCapacityFailure(
                    LlmErrorCategory.TIMEOUT,
                    null,
                    error.getMessage(),
                    "timeout"
            ));
        }
        return Optional.empty();
    }

    private Optional<LlmCapacityFailure> classifyStructured(LlmStructuredException structured) {
        LlmErrorCategory category = structured.errorCategory();
        if (category == null) {
            Integer statusCode = structured.httpStatusCode();
            if (statusCode != null && statusCode == 429) {
                category = LlmErrorCategory.PLATFORM_429;
            } else if (statusCode != null && statusCode >= 500 && statusCode < 600) {
                category = LlmErrorCategory.HTTP_5XX;
            }
        }
        if (!isCapacityCategory(category)) {
            return Optional.empty();
        }
        return Optional.of(new LlmCapacityFailure(
                category,
                sanitizeRetryAfterMs(structured.retryAfterMs()),
                structured.providerErrorCode(),
                structured.getClass().getSimpleName()
        ));
    }

    private Optional<LlmCapacityFailure> classifyRoute(LlmRouteException routeException) {
        LlmErrorCategory category = switch (routeException.failureKind()) {
            case ALL_PERMIT_BUSY -> LlmErrorCategory.PERMIT_BUSY;
            case ALL_RATE_LIMITED, ALL_CIRCUIT_OPEN -> LlmErrorCategory.INTERNAL_RATE_LIMITED;
            default -> null;
        };
        if (category == null) {
            return Optional.empty();
        }
        return Optional.of(new LlmCapacityFailure(
                category,
                null,
                routeException.failureKind().name(),
                routeException.getClass().getSimpleName()
        ));
    }

    private boolean isCapacityCategory(LlmErrorCategory category) {
        return category == LlmErrorCategory.PERMIT_BUSY
                || category == LlmErrorCategory.INTERNAL_RATE_LIMITED
                || category == LlmErrorCategory.PLATFORM_429
                || category == LlmErrorCategory.HTTP_5XX
                || category == LlmErrorCategory.TIMEOUT;
    }

    private Long sanitizeRetryAfterMs(Long retryAfterMs) {
        return retryAfterMs == null || retryAfterMs <= 0L ? null : retryAfterMs;
    }

    private boolean containsTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <T> T find(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
