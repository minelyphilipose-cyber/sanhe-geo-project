package com.huanjing.geo.module.content.douyin.client.exception;

import java.util.Set;

public final class DouyinErrorMapper {
    private static final Set<Long> AUTH_CODES = Set.of(
            10003L, 10007L, 10010L, 10013L, 10014L, 28001003L, 28001008L
    );
    private static final Set<Long> PERMISSION_CODES = Set.of(
            28001014L, 28001016L, 28001018L, 28001019L
    );
    private static final Set<Long> RATE_LIMIT_CODES = Set.of(
            28003017L
    );
    private static final Set<Long> VALIDATION_CODES = Set.of(
            10002L, 10005L, 210005L, 2100005L, 2114001L, 2190005L, 28001007L
    );
    private static final Set<Long> SERVER_CODES = Set.of(
            10001L, 2100004L
    );

    private DouyinErrorMapper() {
    }

    public static DouyinClientException toException(int httpStatus,
                                                    Long errorCode,
                                                    String description,
                                                    String logId,
                                                    String rawBody) {
        if (errorCode == null) {
            return new DouyinClientException(httpStatus, null, description, logId, false, rawBody);
        }
        boolean retryable = isRetryable(errorCode);
        if (AUTH_CODES.contains(errorCode)) {
            return new DouyinAuthException(httpStatus, errorCode, description, logId, retryable, rawBody);
        }
        if (PERMISSION_CODES.contains(errorCode)) {
            return new DouyinPermissionException(httpStatus, errorCode, description, logId, retryable, rawBody);
        }
        if (RATE_LIMIT_CODES.contains(errorCode)) {
            return new DouyinRateLimitException(httpStatus, errorCode, description, logId, retryable, rawBody);
        }
        if (VALIDATION_CODES.contains(errorCode)) {
            return new DouyinValidationException(httpStatus, errorCode, description, logId, retryable, rawBody);
        }
        if (SERVER_CODES.contains(errorCode)) {
            return new DouyinServerException(httpStatus, errorCode, description, logId, retryable, rawBody);
        }
        return new DouyinClientException(httpStatus, errorCode, description, logId, false, rawBody);
    }

    public static boolean isRetryable(Long errorCode) {
        if (errorCode == null) {
            return false;
        }
        return SERVER_CODES.contains(errorCode);
    }
}
