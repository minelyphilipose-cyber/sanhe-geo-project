package com.huanjing.geo.module.retention.poll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.entity.PollAuditPurgeRun;
import com.huanjing.geo.module.dispatch.websearch.purge.PollAuditPurgeAuditWriter;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.LockedPollMutationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.OperationReplayStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollCleanupOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollRetryScope;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RelationType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptResult;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionOperationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus;
import com.huanjing.geo.module.retention.audit.port.PollPurgeAuditIdentityWriter;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditJsonCodec;
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
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PollRetentionDomainExecutorTest {
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");
    private static final LocalDate CUTOFF = LocalDate.of(2026, 7, 31);
    private static final RetentionCompatibilityContract CONTRACT = new RetentionCompatibilityContract(
            "poll_results", 1, new byte[32], "poll-results-v2");

    @Test
    void cleanupAlwaysUsesFirstTransactionAndLeavesNineteenSlotsForPrimaryBacklog() {
        Fixture fixture = fixture(ExecutionSource.MANUAL, 20, 20);
        PollRetentionBusinessRepository.CleanupSelection cleanup = new PollRetentionBusinessRepository.CleanupSelection(
                uuid(900), slice(0), List.of(501L, 502L));
        when(fixture.business.selectCleanup(any(), eq(20))).thenReturn(cleanup);
        AtomicInteger selected = new AtomicInteger();
        when(fixture.business.selectNextPrimary(any(), any())).thenAnswer(invocation -> {
            int next = selected.incrementAndGet();
            return next <= 20 ? slice(next) : null;
        });
        when(fixture.business.preflightPrimary(any(), eq(7), any())).thenReturn(true);
        fixture.stubSuccessfulMutations();

        RetentionExecutionResult result = fixture.executor.executeFresh(fixture.session);

        assertEquals(ExecutionResultCode.COMPLETED, result.code());
        assertEquals(21, result.candidateCount());
        assertEquals(21, result.affectedCount());
        verify(fixture.business, times(19)).mutatePrimary(any(), eq(7), any());
        verify(fixture.session, times(20)).inMutationTransaction(any(), any());
        InOrder order = inOrder(fixture.business);
        order.verify(fixture.business).mutateCleanup(any());
        order.verify(fixture.business).mutatePrimary(any(), eq(7), any());
    }

    @Test
    void canaryNeverSelectsCleanupAndRedisUnavailableSkipsPreparedAttempt() {
        Fixture fixture = fixture(ExecutionSource.CANARY, 1, 1);
        PollRetentionBusinessRepository.PollSlice candidate = slice(1);
        AtomicInteger selected = new AtomicInteger();
        when(fixture.business.selectNextPrimary(any(), any())).thenAnswer(
                invocation -> selected.getAndIncrement() == 0 ? candidate : null);
        when(fixture.business.preflightPrimary(candidate, 7, LocalDate.of(2026, 8, 14))).thenReturn(true);
        doThrow(new DynamicSwitchException(409, "REDIS_UNAVAILABLE", "redis unavailable"))
                .when(fixture.session).inMutationTransaction(any(), any());

        RetentionExecutionResult result = fixture.executor.executeFresh(fixture.session);

        assertEquals(ExecutionResultCode.EXECUTE_DYNAMIC_DISABLED, result.code());
        assertEquals("REDIS_UNAVAILABLE", result.stopReason());
        verify(fixture.business, never()).selectCleanup(any(), any(Integer.class));
        verify(fixture.audit, never()).markAttemptRunning(anyLong());
        ArgumentCaptor<RetentionAttemptResult> recovery = ArgumentCaptor.forClass(RetentionAttemptResult.class);
        verify(fixture.audit).recoverAttempt(anyLong(), recovery.capture());
        assertEquals(AttemptStatus.SKIPPED, recovery.getValue().status());
        assertEquals("recovery_not_started", recovery.getValue().stableErrorCode());
    }

    @Test
    void cleanupKnownRollbackKeepsOwnershipAttemptFailedAndContinuesPrimary() {
        Fixture fixture = fixture(ExecutionSource.MANUAL, 2, 2);
        PollRetentionBusinessRepository.CleanupSelection cleanup = new PollRetentionBusinessRepository.CleanupSelection(
                uuid(900), slice(0), List.of(501L));
        PollRetentionBusinessRepository.PollSlice primary = slice(1);
        when(fixture.business.selectCleanup(any(), eq(20))).thenReturn(cleanup);
        AtomicInteger selected = new AtomicInteger();
        when(fixture.business.selectNextPrimary(any(), any())).thenAnswer(
                invocation -> selected.getAndIncrement() == 0 ? primary : null);
        when(fixture.business.preflightPrimary(primary, 7, LocalDate.of(2026, 8, 14))).thenReturn(true);
        when(fixture.business.mutateCleanup(any())).thenThrow(
                new DataRetentionExecutionException(409, "POLL_SLICE_STATE_CHANGED", "changed"));
        fixture.stubPrimarySuccess();

        RetentionExecutionResult result = fixture.executor.executeFresh(fixture.session);

        assertEquals(ExecutionResultCode.PARTIAL, result.code());
        assertEquals(1, result.candidateCount());
        assertEquals(1, result.affectedCount());
        assertEquals(1, result.warningCount());
        verify(fixture.business).mutatePrimary(any(), eq(7), any());
        ArgumentCaptor<RetentionAttemptResult> failed = ArgumentCaptor.forClass(RetentionAttemptResult.class);
        verify(fixture.audit).recoverAttempt(eq(101L), failed.capture());
        assertEquals(AttemptStatus.FAILED, failed.getValue().status());
        assertEquals("DATA_CONTINUE", failed.getValue().metrics().get("failureDisposition"));
    }

    @Test
    void cleanupExactRetryUsesCleanupLimitInsteadOfPrimaryRequestLimit() {
        Fixture fixture = fixture(ExecutionSource.RETRY, 1, 1);
        List<Long> resultIds = LongStream.rangeClosed(501, 520).boxed().toList();
        String operationId = uuid(901);
        RetentionOperationContext persisted = operation(operationId, "poll_preserved_cleanup", 101L,
                resultIds, slice(0));
        when(fixture.audit.readPollRetryScope(any())).thenReturn(new PollRetryScope(persisted, 101L, resultIds));
        RetentionAttemptContext retryAttempt = new RetentionAttemptContext(102L, 77L, operationId, 2, 2,
                com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptType.RETRY,
                AttemptStatus.PREPARED, uuid(102));
        when(fixture.audit.prepareRelatedAttempt(any(), eq(operationId), eq(RelationType.RETRY), any()))
                .thenReturn(retryAttempt);
        when(fixture.business.mutateCleanup(any())).thenReturn(
                new PollRetentionBusinessRepository.CleanupMutationResult(
                        uuid(900), slice(0), resultIds, Map.of("poll_results", 20L)));

        RetentionExecutionResult result = fixture.executor.executeFresh(fixture.session);

        assertEquals(ExecutionResultCode.COMPLETED, result.code());
        assertEquals(20, result.candidateCount());
        assertEquals(20, result.affectedCount());
        verify(fixture.business).mutateCleanup(any());
    }

    private static Fixture fixture(ExecutionSource source, int requestLimit, int maxTransactions) {
        PollRetentionBusinessRepository business = mock(PollRetentionBusinessRepository.class);
        PollAuditPurgeAuditWriter legacyAudit = mock(PollAuditPurgeAuditWriter.class);
        PollPurgeAuditIdentityWriter legacyIdentity = mock(PollPurgeAuditIdentityWriter.class);
        DataRetentionP0AuditService audit = mock(DataRetentionP0AuditService.class);
        RetentionExecutionSession session = mock(RetentionExecutionSession.class);
        RetentionRunContext run = new RetentionRunContext(77L, uuid(77), source.name().toLowerCase(),
                "execute", CONTRACT, "test", "test", RunReplayStatus.CREATED, "running", NOW);
        RetentionExecuteCommand command = new RetentionExecuteCommand(uuid(78), RetentionDomain.POLL_RESULTS,
                source, 9L, "test", "APR-7", null, CONTRACT,
                Map.of("effectiveConfig", Map.of("hotRetentionDays", 14, "stuckBatchSealDays", 7),
                        "requestLimit", requestLimit, "effectiveEndDate", CUTOFF.toString()),
                null, CUTOFF, new ExecutionBudget(maxTransactions, 100, Duration.ofMinutes(10)), null);
        when(session.run()).thenReturn(run);
        when(session.command()).thenReturn(command);
        when(session.progress()).thenReturn(new ExecutionProgress(0, 0, NOW.plus(Duration.ofMinutes(10))));
        when(session.audit()).thenReturn(audit);
        when(session.isStopped()).thenReturn(false);

        AtomicLong attemptIds = new AtomicLong(100);
        when(audit.preparePollCleanupOrigin(eq(run), any(), any())).thenAnswer(invocation -> operation(
                uuid(201), "poll_preserved_cleanup", attemptIds.incrementAndGet(),
                ((PollCleanupOperationCommand) invocation.getArgument(1)).pollResultIds(), slice(0)));
        when(audit.preparePollOrigin(eq(run), any(), any())).thenAnswer(invocation -> {
            PollOperationCommand value = invocation.getArgument(1);
            PollRetentionBusinessRepository.PollSlice selected = new PollRetentionBusinessRepository.PollSlice(
                    value.projectId(), value.batchDate(), value.questionTier());
            return operation(uuid(300 + attemptIds.intValue()), "poll_slice", attemptIds.incrementAndGet(),
                    List.of(), selected);
        });
        when(audit.lockPollMutation(any(), eq(run.runId()), anyLong())).thenAnswer(invocation -> {
            RetentionOperationContext operation = invocation.getArgument(0);
            return new LockedPollMutationContext(operation, run.runId(), invocation.getArgument(2),
                    operation.pollCleanupResultIds());
        });
        AtomicLong legacyIds = new AtomicLong(1000);
        when(legacyAudit.prepareForRetentionOperation(any(), any(), eq(run.runId()), any(), any())).thenAnswer(i -> {
            PollAuditPurgeRun value = new PollAuditPurgeRun();
            value.setId(legacyIds.incrementAndGet());
            return value;
        });
        when(session.inMutationTransaction(any(), any())).thenAnswer(invocation -> {
            RetentionMutationWork<?> work = invocation.getArgument(1);
            PendingMutation<?> pending = work.execute();
            return new CommittedMutation<>(pending.value(), pending.reportedActual(), pending.trustedReceipt(), NOW);
        });
        PollRetentionDomainExecutor executor = new PollRetentionDomainExecutor(business, legacyAudit, legacyIdentity,
                new DataRetentionAuditJsonCodec(new ObjectMapper()), Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(business, legacyAudit, legacyIdentity, audit, session, executor);
    }

    private static RetentionOperationContext operation(String id, String type, long attemptId, List<Long> ids,
                                                       PollRetentionBusinessRepository.PollSlice slice) {
        RetentionAttemptContext attempt = new RetentionAttemptContext(attemptId, 77L, id, 1, 1,
                com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptType.NORMAL_BATCH,
                AttemptStatus.PREPARED, uuid((int) attemptId));
        return new RetentionOperationContext(id, 77L, type, new byte[32], 1,
                "poll_preserved_cleanup".equals(type) ? ids.size() : 1, null, null,
                slice.projectId(), slice.batchDate(), slice.questionTier(), CUTOFF, CONTRACT,
                "test", "test", OperationReplayStatus.CREATED, "pending", attempt, ids);
    }

    private static PollRetentionBusinessRepository.PollSlice slice(int offset) {
        return new PollRetentionBusinessRepository.PollSlice(71L + offset,
                LocalDate.of(2026, 7, 1).plusDays(offset), "A");
    }

    private static String uuid(int suffix) {
        return String.format("00000000-0000-0000-0000-%012d", suffix);
    }

    private record Fixture(PollRetentionBusinessRepository business,
                           PollAuditPurgeAuditWriter legacyAudit,
                           PollPurgeAuditIdentityWriter legacyIdentity,
                           DataRetentionP0AuditService audit,
                           RetentionExecutionSession session,
                           PollRetentionDomainExecutor executor) {
        private void stubSuccessfulMutations() {
            when(business.mutateCleanup(any())).thenReturn(new PollRetentionBusinessRepository.CleanupMutationResult(
                    uuid(900), slice(0), List.of(501L, 502L), Map.of("poll_results", 2L)));
            stubPrimarySuccess();
        }

        private void stubPrimarySuccess() {
            when(business.mutatePrimary(any(), eq(7), any())).thenAnswer(invocation -> {
                LockedPollMutationContext locked = invocation.getArgument(0);
                RetentionOperationContext operation = locked.operation();
                PollRetentionBusinessRepository.PollSlice selected = new PollRetentionBusinessRepository.PollSlice(
                        operation.pollProjectId(), operation.pollBatchDate(), operation.pollQuestionTier());
                return new PollRetentionBusinessRepository.PrimaryMutationResult(selected, List.of(),
                        Map.of("poll_results", 1L));
            });
        }
    }
}
