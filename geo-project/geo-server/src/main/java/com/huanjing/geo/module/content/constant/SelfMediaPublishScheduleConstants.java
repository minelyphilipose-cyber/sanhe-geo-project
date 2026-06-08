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
            STATUS_PUBLISH_UNKNOWN,
            STATUS_CANCEL_PENDING_PLATFORM
    );

    private SelfMediaPublishScheduleConstants() {
    }
}
