package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.SelfMediaPublishFailureCodes;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single source of truth for operator actions on a self-media schedule.
 *
 * <p>The UI must not infer retry semantics from translated error messages.  In
 * particular, an execution retry, a publish-result recheck and a deliberate
 * republish cross different idempotency boundaries.</p>
 */
public final class SelfMediaPublishScheduleActionPolicy {
    public static final String PHASE_SCHEDULE_EXECUTION = "schedule_execution";
    public static final String PHASE_EXECUTION_UNCERTAIN = "execution_uncertain";
    public static final String PHASE_PUBLISH_RESULT_CHECK = "publish_result_check";
    public static final String PHASE_TERMINAL = "terminal";

    public static final String ACTION_RETRY_EXECUTION = "RETRY_EXECUTION";
    public static final String ACTION_REPUBLISH = "REPUBLISH";
    public static final String ACTION_RECHECK_PUBLISH_RESULT = "RECHECK_PUBLISH_RESULT";
    public static final String ACTION_CONFIRM_PUBLISHED = "CONFIRM_PUBLISHED";
    public static final String ACTION_CONFIRM_FAILED = "CONFIRM_FAILED";
    public static final String ACTION_MARK_MANUAL = "MARK_MANUAL";
    public static final String ACTION_CANCEL = "CANCEL";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            SelfMediaPublishScheduleConstants.STATUS_CANCELLED,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED,
            SelfMediaPublishScheduleConstants.STATUS_ROUTED_TO_SEMI_AUTO
    );
    private static final Set<String> RESULT_STATUSES = Set.of(
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
            SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_URL_PENDING,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED
    );
    private static final Set<String> EXECUTION_RETRY_STATUSES = Set.of(
            SelfMediaPublishScheduleConstants.STATUS_PENDING,
            SelfMediaPublishScheduleConstants.STATUS_FILLING,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED,
            SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED
    );
    private static final Set<String> UNCERTAIN_REPUBLISH_STATUSES = Set.of(
            SelfMediaPublishScheduleConstants.STATUS_FILLING,
            SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULING,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED,
            SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED
    );
    private static final Set<String> PENDING_ACTION_CODES = Set.of(
            "manual_retry_requested",
            "auto_compensation_retry",
            "manual_republish_requested",
            "publish_result_recheck_requested"
    );

    private SelfMediaPublishScheduleActionPolicy() {
    }

    public static Decision evaluate(SelfMediaPublishSchedule row,
                                    boolean supportsPublishCheck,
                                    LocalDateTime now) {
        if (row == null) {
            return Decision.none(PHASE_TERMINAL);
        }
        String status = normalize(row.getStatus());
        if (TERMINAL_STATUSES.contains(status)) {
            return Decision.none(PHASE_TERMINAL);
        }

        boolean locked = row.getLockedUntil() != null && row.getLockedUntil().isAfter(now);
        boolean actionPending = PENDING_ACTION_CODES.contains(normalize(row.getFailureCode()));
        boolean publishResult = isPublishResultContext(row);
        boolean uncertainExecution = !publishResult && isUncertainExecution(row);
        String phase = publishResult
                ? PHASE_PUBLISH_RESULT_CHECK
                : uncertainExecution ? PHASE_EXECUTION_UNCERTAIN : PHASE_SCHEDULE_EXECUTION;

        boolean retryExecution = PHASE_SCHEDULE_EXECUTION.equals(phase)
                && EXECUTION_RETRY_STATUSES.contains(status)
                && hasExecutionFailure(row)
                && !locked
                && !actionPending;
        boolean republish = PHASE_EXECUTION_UNCERTAIN.equals(phase)
                && UNCERTAIN_REPUBLISH_STATUSES.contains(status)
                && !locked
                && !actionPending;
        boolean recheck = PHASE_PUBLISH_RESULT_CHECK.equals(phase)
                && supportsPublishCheck
                && !locked
                && !actionPending;
        boolean confirmResult = PHASE_PUBLISH_RESULT_CHECK.equals(phase)
                && !locked
                && !SelfMediaPublishScheduleConstants.STATUS_CANCEL_PENDING_PLATFORM.equals(status);
        boolean markManual = !locked
                && !SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED.equals(status)
                && !SelfMediaPublishScheduleConstants.STATUS_CANCEL_PENDING_PLATFORM.equals(status);
        boolean cancel = !locked
                && !Set.of(
                SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED,
                SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED,
                SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED,
                SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_URL_PENDING,
                SelfMediaPublishScheduleConstants.STATUS_CANCEL_PENDING_PLATFORM
        ).contains(status);

        List<String> actions = new ArrayList<>();
        if (retryExecution) actions.add(ACTION_RETRY_EXECUTION);
        if (republish) actions.add(ACTION_REPUBLISH);
        if (recheck) actions.add(ACTION_RECHECK_PUBLISH_RESULT);
        if (confirmResult) {
            actions.add(ACTION_CONFIRM_PUBLISHED);
            actions.add(ACTION_CONFIRM_FAILED);
        }
        if (markManual) actions.add(ACTION_MARK_MANUAL);
        if (cancel) actions.add(ACTION_CANCEL);
        return new Decision(
                phase,
                retryExecution,
                republish,
                recheck,
                confirmResult,
                confirmResult,
                markManual,
                cancel,
                List.copyOf(actions)
        );
    }

    /** Matches the capability router's current default while keeping static VO conversion deterministic. */
    public static boolean defaultSupportsPublishCheck(String platform) {
        return !"zhihu".equals(normalize(platform));
    }

    public static boolean isPublishResultContext(SelfMediaPublishSchedule row) {
        if (row == null) return false;
        String status = normalize(row.getStatus());
        if (RESULT_STATUSES.contains(status)) return true;
        if (SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK.equals(normalize(row.getQueueKind()))) return true;
        if (row.getScheduledAt() != null
                || row.getPublishedConfirmedAt() != null
                || StringUtils.hasText(row.getPlatformScheduleId())
                || StringUtils.hasText(row.getPlatformPublishId())
                || StringUtils.hasText(row.getPlatformPublishedUrl())) return true;
        if (SelfMediaPublishFailureCodes.isPostSubmissionVerificationFailure(row.getFailureCode())) return true;
        String code = normalize(row.getFailureCode());
        if (code.startsWith("publish_result")
                || code.startsWith("published_url")
                || code.contains("publish_check")
                || code.endsWith("_publish_not_confirmed")
                || "works_list_verify_timeout".equals(code)) return true;
        return Set.of(
                "publish_submitting",
                "publish_submitted",
                "publish_result_recheck",
                "publish_checking",
                "publish_result_manual_confirmation",
                "published_confirmed",
                "published_url_pending"
        ).contains(normalize(row.getRuntimeStage())) || diagnosticsIndicatesSubmission(row.getDiagnosticsJson());
    }

    public static boolean isUncertainExecution(SelfMediaPublishSchedule row) {
        if (row == null) return false;
        String status = normalize(row.getStatus());
        if (SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULING.equals(status)) return true;
        if (Set.of(
                "content_filled",
                "execution_heartbeat_timeout_uncertain",
                "execution_failed_after_page_mutation",
                "execution_state_quarantined"
        ).contains(normalize(row.getRuntimeStage()))) return true;
        String failureCode = normalize(row.getFailureCode());
        return "local_agent_heartbeat_timeout".equals(failureCode)
                || failureCode.contains("cover_upload_timeout")
                || diagnosticsIndicatesMutation(row.getDiagnosticsJson());
    }

    private static boolean hasExecutionFailure(SelfMediaPublishSchedule row) {
        String status = normalize(row.getStatus());
        return SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED.equals(status)
                || StringUtils.hasText(row.getFailureCode())
                || StringUtils.hasText(row.getFailureMessage());
    }

    private static boolean diagnosticsIndicatesSubmission(String diagnosticsJson) {
        return Set.of("submitting_publish", "verifying_publish_result", "completed")
                .contains(diagnosticStage(diagnosticsJson));
    }

    private static boolean diagnosticsIndicatesMutation(String diagnosticsJson) {
        return Set.of(
                "filling_title",
                "filling_content",
                "filling_tags",
                "verifying_content",
                "filling_publish_options",
                "filling_cover",
                "filling_location",
                "configuring_schedule"
        ).contains(diagnosticStage(diagnosticsJson));
    }

    private static String diagnosticStage(String diagnosticsJson) {
        if (!StringUtils.hasText(diagnosticsJson)) return "";
        try {
            JsonNode root = OBJECT_MAPPER.readTree(diagnosticsJson);
            String direct = normalize(root.path("lastStage").asText(""));
            if (StringUtils.hasText(direct)) return direct;
            return normalize(root.path("error")
                    .path("diagnostics")
                    .path("page")
                    .path("activeFillTask")
                    .path("stage")
                    .asText(""));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Decision(
            String phase,
            boolean canRetryExecution,
            boolean canRepublish,
            boolean canRecheckPublishResult,
            boolean canConfirmPublished,
            boolean canConfirmFailed,
            boolean canMarkManual,
            boolean canCancel,
            List<String> availableActions
    ) {
        private static Decision none(String phase) {
            return new Decision(phase, false, false, false, false, false, false, false, List.of());
        }
    }
}
