package com.huanjing.geo.module.retention.website;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ExecutionBudgetUsage;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.LockedWebsiteMutationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.OperationReplayStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptResult;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionOperationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteOperationCommand;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.execute.DataRetentionExecutionException;
import com.huanjing.geo.module.retention.execute.RetentionExecutionSession;
import com.huanjing.geo.module.retention.execute.RetentionMutationWork;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.CommittedMutation;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionBudget;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionProgress;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionResultCode;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PendingMutation;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecuteCommand;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecutionResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebsiteRetentionDomainExecutorTest {

    @Test
    void normalizesSeventeenCandidatesAsTenFiveTwo() {
        int first = WebsiteRetentionDomainExecutor.normalizedBatchSize(17, 17);
        int second = WebsiteRetentionDomainExecutor.normalizedBatchSize(7, 7);
        int third = WebsiteRetentionDomainExecutor.normalizedBatchSize(2, 2);

        assertEquals(10, first);
        assertEquals(5, second);
        assertEquals(2, third);
    }

    @Test
    void keepsNaturalTailAndCanonicalLargeBatchSizes() {
        assertEquals(20, WebsiteRetentionDomainExecutor.normalizedBatchSize(20, 20));
        assertEquals(10, WebsiteRetentionDomainExecutor.normalizedBatchSize(19, 19));
        assertEquals(5, WebsiteRetentionDomainExecutor.normalizedBatchSize(9, 9));
        assertEquals(4, WebsiteRetentionDomainExecutor.normalizedBatchSize(4, 4));
        assertEquals(1, WebsiteRetentionDomainExecutor.normalizedBatchSize(1, 1));
    }

    @Test
    void classifiesRollbackDispositionWithDefaultSafetyStop() {
        assertEquals(WebsiteRetentionDomainExecutor.RollbackDisposition.DATA_SPLIT_ELIGIBLE,
                WebsiteRetentionDomainExecutor.classifyKnownRollback(
                        new DataRetentionExecutionException(409, "WEBSITE_BATCH_STATE_CHANGED", "changed")));
        assertEquals(WebsiteRetentionDomainExecutor.RollbackDisposition.TRANSIENT_SAME_SCOPE_RETRY,
                WebsiteRetentionDomainExecutor.classifyKnownRollback(
                        new CannotAcquireLockException("lock timeout")));
        assertEquals(WebsiteRetentionDomainExecutor.RollbackDisposition.SAFETY_STOP,
                WebsiteRetentionDomainExecutor.classifyKnownRollback(
                        new IllegalStateException("unclassified")));
    }

    @Test
    void secondBatchBudgetExhaustionSkipsPreparedAttemptBeforeMutationStarts() {
        assertSecondCheckpointStopsBeforeMutation(
                new DataRetentionExecutionException(409, "EXECUTE_BUDGET_EXHAUSTED", "budget exhausted"),
                ExecutionResultCode.EXECUTE_BUDGET_EXHAUSTED,
                "EXECUTE_BUDGET_EXHAUSTED");
    }

    @Test
    void runtimeDynamicGateDisableSkipsPreparedAttemptBeforeMutationStarts() {
        assertSecondCheckpointStopsBeforeMutation(
                new DynamicSwitchException(409, "DOMAIN_DISABLED", "domain disabled"),
                ExecutionResultCode.EXECUTE_DYNAMIC_DISABLED,
                "DOMAIN_DISABLED");
    }

    @Test
    void runtimeDynamicGateRedisUnavailableSkipsPreparedAttemptBeforeMutationStarts() {
        assertSecondCheckpointStopsBeforeMutation(
                new DynamicSwitchException(409, "REDIS_UNAVAILABLE", "redis unavailable"),
                ExecutionResultCode.EXECUTE_DYNAMIC_DISABLED,
                "REDIS_UNAVAILABLE");
    }

    private void assertSecondCheckpointStopsBeforeMutation(RuntimeException checkpointFailure,
                                                            ExecutionResultCode expectedCode,
                                                            String expectedStopReason) {
        Instant now = Instant.parse("2026-08-14T02:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        WebsiteRetentionBusinessRepository business = mock(WebsiteRetentionBusinessRepository.class);
        DataRetentionP0AuditService audit = mock(DataRetentionP0AuditService.class);
        RetentionExecutionSession session = mock(RetentionExecutionSession.class);
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                "website_published_content", 1, new byte[32], "website-published-v1");
        RetentionRunContext run = new RetentionRunContext(77L,
                "00000000-0000-0000-0000-000000000077", "manual", "execute", contract,
                "test", "test", RunReplayStatus.CREATED, "running", now);
        RetentionExecuteCommand command = new RetentionExecuteCommand(
                "00000000-0000-0000-0000-000000000078", RetentionDomain.WEBSITE_PUBLISHED_CONTENT,
                ExecutionSource.MANUAL, 9L, "test", "APR-1", null, contract,
                Map.of("effectiveConfig", Map.of("retentionHours", 24), "requestLimit", 25),
                null, null, new ExecutionBudget(1, 25, Duration.ofMinutes(10)), null);
        ExecutionProgress progress = new ExecutionProgress(0, 0, now.plus(Duration.ofMinutes(10)));
        List<Long> firstBatch = LongStream.rangeClosed(1, 20).boxed().toList();
        List<Long> secondBatch = LongStream.rangeClosed(21, 25).boxed().toList();

        when(session.run()).thenReturn(run);
        when(session.command()).thenReturn(command);
        when(session.progress()).thenReturn(progress);
        when(session.audit()).thenReturn(audit);
        when(session.isStopped()).thenReturn(false);
        when(business.selectInitial(any(), eq(20))).thenReturn(
                new WebsiteRetentionBusinessRepository.Selection(25L, firstBatch));
        when(business.selectCandidates(any(), eq(25L), eq(5))).thenReturn(secondBatch);

        AtomicLong attemptIds = new AtomicLong(100L);
        when(audit.prepareWebsiteOrigin(eq(run), any(), any())).thenAnswer(invocation -> {
            WebsiteOperationCommand operationCommand = invocation.getArgument(1);
            RetentionAttemptCommand attemptCommand = invocation.getArgument(2);
            long attemptId = attemptIds.incrementAndGet();
            String operationId = "00000000-0000-0000-0000-000000000" + (attemptId == 101L ? "101" : "102");
            RetentionAttemptContext attempt = new RetentionAttemptContext(
                    attemptId, run.runId(), operationId, (int) (attemptId - 100L), 1,
                    attemptCommand.attemptType(), AttemptStatus.PREPARED, attemptCommand.attemptRequestId());
            return new RetentionOperationContext(operationId, run.runId(), "website_batch", new byte[32],
                    1, operationCommand.articleIds().size(), operationCommand.cutoff(),
                    operationCommand.upperBoundBusinessKey(), null, null, null, null, contract,
                    "test", "test", OperationReplayStatus.CREATED, "pending", attempt,
                    operationCommand.articleIds());
        });
        when(audit.lockWebsiteMutation(any(), eq(run.runId()), any(Long.class))).thenAnswer(invocation -> {
            RetentionOperationContext operation = invocation.getArgument(0);
            Long attemptId = invocation.getArgument(2);
            return new LockedWebsiteMutationContext(
                    operation, run.runId(), attemptId, operation.websiteArticleIds(), null);
        });
        when(business.mutate(any(), any())).thenReturn(
                new WebsiteRetentionBusinessRepository.MutationResult(20, 0, 20, 0, 20));

        AtomicInteger transactionCalls = new AtomicInteger();
        when(session.inMutationTransaction(any(), any())).thenAnswer(invocation -> {
            if (transactionCalls.incrementAndGet() == 2) throw checkpointFailure;
            RetentionMutationWork<?> work = invocation.getArgument(1);
            PendingMutation<?> pending = work.execute();
            return new CommittedMutation<>(pending.value(), pending.reportedActual(),
                    pending.trustedReceipt(), now);
        });

        RetentionExecutionResult result = new WebsiteRetentionDomainExecutor(business, clock).executeFresh(session);

        assertEquals(expectedCode, result.code());
        assertEquals(expectedStopReason, result.stopReason());
        assertEquals(20, result.candidateCount());
        assertEquals(20, result.affectedCount());
        verify(audit).markAttemptRunning(101L);
        verify(audit, never()).markAttemptRunning(102L);
        ArgumentCaptor<RetentionAttemptResult> recovery = ArgumentCaptor.forClass(RetentionAttemptResult.class);
        verify(audit).recoverAttempt(eq(102L), recovery.capture());
        assertEquals(AttemptStatus.SKIPPED, recovery.getValue().status());
        assertEquals("recovery_not_started", recovery.getValue().stableErrorCode());
        assertEquals(new ExecutionBudgetUsage(1, 0, 0),
                recovery.getValue().metrics().get("executionBudgetUsage"));
        verify(audit, never()).completeAttempt(eq(102L), any());
    }
}
