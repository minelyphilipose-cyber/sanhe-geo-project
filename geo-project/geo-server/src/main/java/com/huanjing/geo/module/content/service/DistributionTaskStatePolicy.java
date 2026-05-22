package com.huanjing.geo.module.content.service;

import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * Central contract for distribution task status combinations.
 */
public final class DistributionTaskStatePolicy {

    public static final String MODE_AUTO = "AUTO";
    public static final String MODE_SEMI_AUTO = "SEMI_AUTO";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SUBMITTING = "submitting";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_TOKEN_ISSUED = "token_issued";
    public static final String STATUS_FILLING = "filling";
    public static final String STATUS_FILLED = "filled";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_FAILED = "failed";

    public static final String REVIEW_NOT_APPLICABLE = "not_applicable";
    public static final String REVIEW_UNKNOWN = "unknown";
    public static final String REVIEW_UNDER_REVIEW = "under_review";
    public static final String REVIEW_PUBLISHED = "published";
    public static final String REVIEW_REJECTED = "rejected";
    public static final String REVIEW_OFFLINE = "offline";

    private static final Set<StatePair> API_AUTO_ALLOWED = Set.of(
            pair(STATUS_PENDING, null),
            pair(STATUS_SUBMITTING, null),
            pair(STATUS_SUBMITTED, REVIEW_UNDER_REVIEW),
            pair(STATUS_SUBMITTED, REVIEW_UNKNOWN),
            pair(STATUS_PUBLISHED, REVIEW_PUBLISHED),
            pair(STATUS_FAILED, REVIEW_REJECTED),
            pair(STATUS_FAILED, REVIEW_UNKNOWN),
            pair(STATUS_PUBLISHED, REVIEW_OFFLINE)
    );

    private static final Set<StatePair> SEMI_AUTO_ALLOWED = Set.of(
            pair(STATUS_PENDING, REVIEW_NOT_APPLICABLE),
            pair(STATUS_TOKEN_ISSUED, REVIEW_NOT_APPLICABLE),
            pair(STATUS_FILLING, REVIEW_NOT_APPLICABLE),
            pair(STATUS_FILLED, REVIEW_NOT_APPLICABLE),
            pair(STATUS_PUBLISHED, REVIEW_NOT_APPLICABLE),
            pair(STATUS_FAILED, REVIEW_NOT_APPLICABLE)
    );

    private static final Set<StatePair> OTHER_ALLOWED = Set.of(
            pair(STATUS_SUBMITTED, REVIEW_NOT_APPLICABLE),
            pair(STATUS_FAILED, REVIEW_NOT_APPLICABLE)
    );

    private static final Map<String, ReviewTransition> REVIEW_TERMINAL_TRANSITIONS = Map.of(
            REVIEW_PUBLISHED, new ReviewTransition(STATUS_PUBLISHED, REVIEW_PUBLISHED),
            REVIEW_REJECTED, new ReviewTransition(STATUS_FAILED, REVIEW_REJECTED),
            REVIEW_OFFLINE, new ReviewTransition(STATUS_PUBLISHED, REVIEW_OFFLINE)
    );

    private DistributionTaskStatePolicy() {
    }

    public static boolean isAllowed(String dispatchMode, String status, String reviewStatus) {
        StatePair pair = pair(status, normalizedReview(reviewStatus));
        if (MODE_SEMI_AUTO.equalsIgnoreCase(trim(dispatchMode))) {
            return SEMI_AUTO_ALLOWED.contains(pair);
        }
        if (MODE_AUTO.equalsIgnoreCase(trim(dispatchMode))) {
            return API_AUTO_ALLOWED.contains(pair) || OTHER_ALLOWED.contains(pair);
        }
        return OTHER_ALLOWED.contains(pair);
    }

    public static void requireAllowed(String dispatchMode, String status, String reviewStatus) {
        if (!isAllowed(dispatchMode, status, reviewStatus)) {
            throw new IllegalArgumentException("illegal distribution task state: dispatchMode=%s status=%s reviewStatus=%s"
                    .formatted(dispatchMode, status, reviewStatus));
        }
    }

    public static String defaultReviewStatus(String dispatchMode, String targetKind) {
        if (MODE_SEMI_AUTO.equalsIgnoreCase(trim(dispatchMode))) {
            return REVIEW_NOT_APPLICABLE;
        }
        if (MODE_AUTO.equalsIgnoreCase(trim(dispatchMode)) && "mp_account".equalsIgnoreCase(trim(targetKind))) {
            return null;
        }
        return REVIEW_NOT_APPLICABLE;
    }

    public static ReviewTransition transitionForReview(String reviewStatus) {
        String normalized = normalizedReview(reviewStatus);
        return REVIEW_TERMINAL_TRANSITIONS.getOrDefault(
                normalized,
                new ReviewTransition(STATUS_SUBMITTED, normalized)
        );
    }

    public static boolean isAutomaticReviewCandidate(String dispatchMode,
                                                     String targetKind,
                                                     String status,
                                                     String reviewStatus) {
        return MODE_AUTO.equalsIgnoreCase(trim(dispatchMode))
                && "mp_account".equalsIgnoreCase(trim(targetKind))
                && STATUS_SUBMITTED.equalsIgnoreCase(trim(status))
                && Set.of(REVIEW_UNDER_REVIEW, REVIEW_UNKNOWN).contains(normalizedReview(reviewStatus));
    }

    public static String normalizedReview(String reviewStatus) {
        if (!StringUtils.hasText(reviewStatus)) {
            return null;
        }
        return reviewStatus.trim().toLowerCase();
    }

    private static StatePair pair(String status, String reviewStatus) {
        return new StatePair(trim(status), normalizedReview(reviewStatus));
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
    }

    private record StatePair(String status, String reviewStatus) {
    }

    public record ReviewTransition(String taskStatus, String reviewStatus) {
    }
}
