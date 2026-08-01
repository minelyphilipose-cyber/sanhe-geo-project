package com.huanjing.geo.module.content.constant;

import java.util.Set;

public final class SelfMediaPublishScheduleConstants {
    public static final String STRATEGY_PLATFORM_SCHEDULE = "platform_schedule";
    public static final String STRATEGY_BACKEND_DELAYED_PUBLISH = "backend_delayed_publish";
    public static final String STRATEGY_SEMI_AUTO = "semi_auto";
    public static final String STRATEGY_IMMEDIATE_PUBLISH_EXCEPTION = "immediate_publish_exception";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_FILLING = "filling";
    public static final String STATUS_FILLED_VERIFIED = "filled_verified";
    public static final String STATUS_SCHEDULING = "scheduling";
    public static final String STATUS_SCHEDULED = "scheduled";
    public static final String STATUS_PUBLISH_DUE = "publish_due";
    public static final String STATUS_CHECKING_PUBLISH_RESULT = "checking_publish_result";
    public static final String STATUS_PUBLISHED_CONFIRMED = "published_confirmed";
    public static final String STATUS_PUBLISHED_URL_PENDING = "published_url_pending";
    public static final String STATUS_PUBLISH_UNKNOWN = "publish_unknown";
    public static final String STATUS_SCHEDULE_FAILED = "schedule_failed";
    public static final String STATUS_PUBLISH_FAILED = "publish_failed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_CANCEL_PENDING_PLATFORM = "cancel_pending_platform";
    public static final String STATUS_MANUAL_REQUIRED = "manual_required";
    public static final String STATUS_ROUTED_TO_SEMI_AUTO = "routed_to_semi_auto";

    public static final String QUEUE_SCHEDULE_EXECUTION = "schedule_execution";
    public static final String QUEUE_PUBLISH_RESULT_CHECK = "publish_result_check";

    public static final Set<String> ACTIVE_STATUSES = Set.of(
            STATUS_PENDING,
            STATUS_FILLING,
            STATUS_FILLED_VERIFIED,
            STATUS_SCHEDULING,
            STATUS_SCHEDULED,
            STATUS_PUBLISH_DUE,
            STATUS_CHECKING_PUBLISH_RESULT,
            STATUS_PUBLISHED_URL_PENDING,
            STATUS_PUBLISH_UNKNOWN,
            STATUS_CANCEL_PENDING_PLATFORM
    );

    /**
     * Statuses that prevent creating another schedule for the same article/account/platform.
     */
    public static final Set<String> DUPLICATE_PROTECTED_STATUSES = Set.of(
            STATUS_PENDING,
            STATUS_FILLING,
            STATUS_FILLED_VERIFIED,
            STATUS_SCHEDULING,
            STATUS_SCHEDULED,
            STATUS_PUBLISH_DUE,
            STATUS_CHECKING_PUBLISH_RESULT,
            STATUS_PUBLISHED_CONFIRMED,
            STATUS_PUBLISHED_URL_PENDING,
            STATUS_PUBLISH_UNKNOWN,
            STATUS_SCHEDULE_FAILED,
            STATUS_CANCEL_PENDING_PLATFORM,
            STATUS_MANUAL_REQUIRED,
            STATUS_ROUTED_TO_SEMI_AUTO
    );

    /**
     * Aggregate schedule statuses that mean the platform has accepted the article.
     * A published aggregate always has precedence over distributing and failed states.
     */
    public static final Set<String> ARTICLE_PUBLISHED_STATUSES = Set.of(
            STATUS_PUBLISHED_CONFIRMED,
            STATUS_PUBLISHED_URL_PENDING
    );

    /**
     * Aggregate schedule statuses that still own the article publishing workflow.
     */
    public static final Set<String> ARTICLE_DISTRIBUTING_STATUSES = Set.of(
            STATUS_PENDING,
            STATUS_FILLING,
            STATUS_FILLED_VERIFIED,
            STATUS_SCHEDULING,
            STATUS_SCHEDULED,
            STATUS_PUBLISH_DUE,
            STATUS_CHECKING_PUBLISH_RESULT,
            STATUS_PUBLISH_UNKNOWN,
            STATUS_CANCEL_PENDING_PLATFORM,
            STATUS_ROUTED_TO_SEMI_AUTO
    );

    /**
     * Terminal statuses that require retry or manual intervention when no active/published
     * schedule exists for the same article.
     */
    public static final Set<String> ARTICLE_FAILED_STATUSES = Set.of(
            STATUS_SCHEDULE_FAILED,
            STATUS_PUBLISH_FAILED,
            STATUS_MANUAL_REQUIRED
    );

    private SelfMediaPublishScheduleConstants() {
    }
}
