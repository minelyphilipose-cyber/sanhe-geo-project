package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaPublishScheduleActionPolicyTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 8, 3, 20, 30);

    @Test
    void executionFailureKeepsRetryVisibleEvenWhenMessageMentionsRetryButton() {
        SelfMediaPublishSchedule row = row(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        row.setFailureCode("DOUYIN_UNPUBLISHED_DRAFT_BLOCKED");
        row.setFailureMessage("抖音存在未完成图文草稿，请处理后再点击立即重试");

        SelfMediaPublishScheduleActionPolicy.Decision decision = decide(row, true);

        assertEquals(SelfMediaPublishScheduleActionPolicy.PHASE_SCHEDULE_EXECUTION, decision.phase());
        assertTrue(decision.canRetryExecution());
        assertFalse(decision.canRepublish());
        assertFalse(decision.canRecheckPublishResult());
    }

    @Test
    void mutatedPageUncertaintyRequiresExplicitRepublish() {
        SelfMediaPublishSchedule row = row(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        row.setFailureCode("LOCAL_AGENT_HEARTBEAT_TIMEOUT");
        row.setRuntimeStage("execution_heartbeat_timeout_uncertain");

        SelfMediaPublishScheduleActionPolicy.Decision decision = decide(row, true);

        assertEquals(SelfMediaPublishScheduleActionPolicy.PHASE_EXECUTION_UNCERTAIN, decision.phase());
        assertFalse(decision.canRetryExecution());
        assertTrue(decision.canRepublish());
        assertFalse(decision.canRecheckPublishResult());
    }

    @Test
    void submittedWorkUsesResultRecheckAndManualConfirmation() {
        SelfMediaPublishSchedule row = row(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        row.setFailureCode("PUBLISH_RESULT_NOT_MATCHED");
        row.setRuntimeStage("publish_submitted");

        SelfMediaPublishScheduleActionPolicy.Decision decision = decide(row, true);

        assertEquals(SelfMediaPublishScheduleActionPolicy.PHASE_PUBLISH_RESULT_CHECK, decision.phase());
        assertFalse(decision.canRetryExecution());
        assertFalse(decision.canRepublish());
        assertTrue(decision.canRecheckPublishResult());
        assertTrue(decision.canConfirmPublished());
        assertTrue(decision.canConfirmFailed());
    }

    @Test
    void platformWithoutAutomaticResultCheckStillAllowsManualConfirmation() {
        SelfMediaPublishSchedule row = row(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);

        SelfMediaPublishScheduleActionPolicy.Decision decision = decide(row, false);

        assertEquals(SelfMediaPublishScheduleActionPolicy.PHASE_PUBLISH_RESULT_CHECK, decision.phase());
        assertFalse(decision.canRecheckPublishResult());
        assertTrue(decision.canConfirmPublished());
        assertTrue(decision.canConfirmFailed());
    }

    @Test
    void queuedOperatorActionDoesNotExposeDuplicateAction() {
        SelfMediaPublishSchedule row = row(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        row.setFailureCode("MANUAL_RETRY_REQUESTED");

        SelfMediaPublishScheduleActionPolicy.Decision decision = decide(row, true);

        assertFalse(decision.canRetryExecution());
        assertFalse(decision.canRepublish());
        assertFalse(decision.canRecheckPublishResult());
    }

    @Test
    void confirmedPublishIsTerminal() {
        SelfMediaPublishSchedule row = row(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED);

        SelfMediaPublishScheduleActionPolicy.Decision decision = decide(row, true);

        assertEquals(SelfMediaPublishScheduleActionPolicy.PHASE_TERMINAL, decision.phase());
        assertTrue(decision.availableActions().isEmpty());
    }

    private SelfMediaPublishScheduleActionPolicy.Decision decide(SelfMediaPublishSchedule row,
                                                                  boolean supportsPublishCheck) {
        return SelfMediaPublishScheduleActionPolicy.evaluate(row, supportsPublishCheck, now);
    }

    private SelfMediaPublishSchedule row(String status) {
        SelfMediaPublishSchedule row = new SelfMediaPublishSchedule();
        row.setPlatform("douyin");
        row.setStatus(status);
        return row;
    }
}
