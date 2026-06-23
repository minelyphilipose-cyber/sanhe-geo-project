package com.huanjing.geo.module.content.service.adapter;

public record ReviewStatusResult(
        ReviewStatus status,
        String externalStatus,
        String reviewFeedback,
        boolean retryable,
        String rawResponse,
        String platformArticleId,
        String publishedUrl
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

    public ReviewStatusResult(ReviewStatus status,
                              String externalStatus,
                              String reviewFeedback,
                              boolean retryable,
                              String rawResponse,
                              String platformArticleId) {
        this(status, externalStatus, reviewFeedback, retryable, rawResponse, platformArticleId, null);
    }

    public ReviewStatusResult(ReviewStatus status,
                              String externalStatus,
                              String reviewFeedback,
                              boolean retryable,
                              String rawResponse) {
        this(status, externalStatus, reviewFeedback, retryable, rawResponse, null, null);
    }
}
