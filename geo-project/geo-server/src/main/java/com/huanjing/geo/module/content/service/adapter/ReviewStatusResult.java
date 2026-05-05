package com.huanjing.geo.module.content.service.adapter;

public record ReviewStatusResult(
        ReviewStatus status,
        String externalStatus,
        String reviewFeedback,
        boolean retryable,
        String rawResponse
) {
    public enum ReviewStatus {
        NOT_APPLICABLE,
        UNKNOWN,
        UNDER_REVIEW,
        PUBLISHED,
        REJECTED,
        OFFLINE
    }

    public static ReviewStatusResult notApplicable() {
        return new ReviewStatusResult(ReviewStatus.NOT_APPLICABLE, null, null, false, null);
    }

    public static ReviewStatusResult unknown(String externalStatus, String reviewFeedback, boolean retryable, String rawResponse) {
        return new ReviewStatusResult(ReviewStatus.UNKNOWN, externalStatus, reviewFeedback, retryable, rawResponse);
    }
}
