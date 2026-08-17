package com.huanjing.geo.module.retention.poll;

import com.huanjing.geo.module.dispatch.entity.PollAuditPurgeRun;
import com.huanjing.geo.module.dispatch.websearch.purge.PollAuditPurgeAuditWriter;
import com.huanjing.geo.module.dispatch.websearch.purge.PollPurgeRequest;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ExecutionBudgetUsage;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollCleanupCommitCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollCleanupOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollCommitCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollRecoveryOperation;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollRetryScope;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RelationType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptResult;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionOperationContext;
import com.huanjing.geo.module.retention.audit.port.PollPurgeAuditIdentityWriter;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditException;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditJsonCodec;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.execute.DataRetentionDomainExecutor;
import com.huanjing.geo.module.retention.execute.DataRetentionExecutionException;
import com.huanjing.geo.module.retention.execute.RetentionExecutionSession;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.CommittedMutation;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionResultCode;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.MutationEstimate;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PendingMutation;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PersistedRunState;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecutionResult;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionMutationActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.POLL_DOMAIN;

@Component
public class PollRetentionDomainExecutor implements DataRetentionDomainExecutor {
    static final int MAX_TRANSIENT_SAME_SCOPE_RETRIES = 2;
    static final int MAX_CLEANUP_RESULTS = 20;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final PollRetentionBusinessRepository business;
    private final PollAuditPurgeAuditWriter legacyAudit;
    private final PollPurgeAuditIdentityWriter legacyIdentity;
    private final DataRetentionAuditJsonCodec json;
    private final Clock clock;

    @Autowired
    public PollRetentionDomainExecutor(PollRetentionBusinessRepository business,
                                       PollAuditPurgeAuditWriter legacyAudit,
                                       PollPurgeAuditIdentityWriter legacyIdentity,
                                       DataRetentionAuditJsonCodec json) {
        this(business, legacyAudit, legacyIdentity, json, Clock.systemUTC());
    }

    PollRetentionDomainExecutor(PollRetentionBusinessRepository business,
                                PollAuditPurgeAuditWriter legacyAudit,
                                PollPurgeAuditIdentityWriter legacyIdentity,
                                DataRetentionAuditJsonCodec json,
                                Clock clock) {
        this.business = business;
        this.legacyAudit = legacyAudit;
        this.legacyIdentity = legacyIdentity;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public RetentionDomain domain() {
        return RetentionDomain.POLL_RESULTS;
    }

    @Override
    public RetentionExecutionResult executeFresh(RetentionExecutionSession session) {
        RequestPolicy policy = requestPolicy(session);
        if (session.command().source() == ExecutionSource.RETRY) {
            return executeExactRetry(session, policy);
        }
        Totals totals = new Totals();
        int transactionSlots = 0;
        int cleanupLimit = cleanupLimit(session);
        if (session.command().source() != ExecutionSource.CANARY && cleanupLimit > 0
                && transactionSlots < session.command().budget().maxMutationTransactions()) {
            PollRetentionBusinessRepository.CleanupSelection cleanup = business.selectCleanup(
                    policy.selection(), cleanupLimit);
            if (cleanup != null) {
                RetentionAttemptCommand attemptCommand = attemptCommand(AttemptType.NORMAL_BATCH,
                        "pollCleanupSchemaVersion");
                RetentionOperationContext operation = session.audit().preparePollCleanupOrigin(session.run(),
                        new PollCleanupOperationCommand(cleanup.originOperationId(), cleanup.slice().projectId(),
                                cleanup.slice().batchDate(), cleanup.slice().questionTier(), policy.cutoff(),
                                cleanup.pollResultIds()), attemptCommand);
                transactionSlots++;
                runMutation(session, operation, operation.firstAttempt(), policy, totals);
            }
        }

        PollRetentionBusinessRepository.PollSlice cursor = null;
        while (!session.isStopped() && !totals.stopped && totals.primarySelections < policy.requestLimit()
                && transactionSlots < session.command().budget().maxMutationTransactions()) {
            PollRetentionBusinessRepository.PollSlice slice = business.selectNextPrimary(policy.selection(), cursor);
            if (slice == null) break;
            cursor = slice;
            if (!business.preflightPrimary(slice, policy.stuckBatchSealDays(), policy.today())) continue;
            AttemptType type = session.command().source() == ExecutionSource.CANARY
                    ? AttemptType.CANARY : AttemptType.NORMAL_BATCH;
            RetentionOperationContext operation = session.audit().preparePollOrigin(session.run(),
                    new PollOperationCommand(slice.projectId(), slice.batchDate(), slice.questionTier(), policy.cutoff()),
                    attemptCommand(type, "pollSliceSchemaVersion"));
            totals.primarySelections++;
            transactionSlots++;
            runMutation(session, operation, operation.firstAttempt(), policy, totals);
        }
        return result(totals);
    }

    @Override
    public RetentionExecutionResult resumeRunning(RetentionExecutionSession session, PersistedRunState state) {
        RequestPolicy policy = requestPolicy(session);
        if (state.emptyTree()) return executeFresh(session);
        Totals totals = new Totals();
        for (var persisted : state.attempts()) {
            if (persisted.status().terminal()) {
                totals.candidates += persisted.candidateCount();
                totals.affected += persisted.affectedCount();
                totals.skipped += persisted.skippedCount();
                totals.warnings += persisted.warningCount();
                if (persisted.status() != AttemptStatus.SUCCEEDED
                        && persisted.status() != AttemptStatus.SKIPPED) totals.partial = true;
                continue;
            }
            if (persisted.operationId() == null) {
                throw new DataRetentionExecutionException(503, "POLL_RECOVERY_EVIDENCE_CONFLICT",
                        "Poll execute recovery contains an operationless attempt");
            }
            PollRecoveryOperation recovery = session.audit().readPollRecoveryOperation(
                    persisted.operationId(), session.run().runId(), persisted.attemptId());
            if (persisted.status() == AttemptStatus.PREPARED) {
                runMutation(session, recovery.operation(), recovery.attempt(), policy, totals);
            } else {
                recoverRunning(session, recovery, totals);
            }
            if (totals.stopped) break;
        }
        return result(totals);
    }

    private RetentionExecutionResult executeExactRetry(RetentionExecutionSession session, RequestPolicy policy) {
        PollRetryScope retry = session.audit().readPollRetryScope(session.command().relatedOperationId());
        if ("poll_preserved_cleanup".equals(retry.operation().operationType())) {
            long remainingCandidateBudget = session.command().budget().maxCandidateItems()
                    - session.progress().usedCandidateItems();
            if (retry.operation().itemCount() < 1
                    || retry.operation().itemCount() > MAX_CLEANUP_RESULTS
                    || retry.operation().itemCount() > remainingCandidateBudget) {
                throw new DataRetentionExecutionException(409, "POLL_SELECTION_INVALID",
                        "Poll cleanup exact retry scope exceeds the cleanup protocol or remaining candidate budget");
            }
        }
        RetentionAttemptContext attempt = session.audit().prepareRelatedAttempt(session.run(),
                retry.operation().operationId(), RelationType.RETRY,
                new RetentionAttemptCommand(UUID.randomUUID().toString(), AttemptType.RETRY,
                        retry.latestAttemptId(), null, Map.of("pollExactRetrySchemaVersion", 1)));
        RetentionOperationContext operation = withAttempt(retry.operation(), attempt, retry.pollResultIds());
        Totals totals = new Totals();
        runMutation(session, operation, attempt, policy, totals);
        return result(totals);
    }

    private void runMutation(RetentionExecutionSession session,
                             RetentionOperationContext operation,
                             RetentionAttemptContext attempt,
                             RequestPolicy policy,
                             Totals totals) {
        int transientRetries = 0;
        AtomicBoolean mutationStarted = new AtomicBoolean(false);
        AtomicReference<Long> legacyAuditId = new AtomicReference<>();
        boolean cleanup = "poll_preserved_cleanup".equals(operation.operationType());
        while (!totals.stopped) {
            try {
                CommittedMutation<?> committed = session.inMutationTransaction(
                        new MutationEstimate(operation.itemCount()), () -> {
                            Instant now = now();
                            if (!cleanup && legacyAuditId.get() == null) {
                                PollAuditPurgeRun prepared = legacyAudit.prepareForRetentionOperation(
                                        legacyRequest(session, operation), local(now), session.run().runId(),
                                        operation.operationId(), POLL_DOMAIN);
                                legacyAuditId.set(prepared.getId());
                            }
                            if (mutationStarted.compareAndSet(false, true)) {
                                session.audit().markAttemptRunning(attempt.attemptId());
                            }
                            if (!cleanup) {
                                legacyAudit.markRunningInMutation(legacyAuditId.get(), session.run().runId(),
                                        operation.operationId(), local(now));
                            }
                            var locked = session.audit().lockPollMutation(operation, session.run().runId(),
                                    attempt.attemptId());
                            if (cleanup) {
                                var mutation = business.mutateCleanup(locked);
                                var receipt = session.audit().commitPollCleanup(new PollCleanupCommitCommand(
                                        locked.operation(), session.run().runId(), attempt.attemptId(),
                                        mutation.originOperationId(), mutation.pollResultIds(), mutation.metrics(), now));
                                RetentionMutationActual actual = new RetentionMutationActual(operation.operationId(),
                                        mutation.pollResultIds().size(), mutation.pollResultIds().size(), 0, 0);
                                return new PendingMutation<>(mutation, actual, receipt);
                            }
                            var mutation = business.mutatePrimary(locked, policy.stuckBatchSealDays(), policy.today());
                            var receipt = session.audit().commitPollMutation(new PollCommitCommand(
                                    locked.operation(), session.run().runId(), attempt.attemptId(),
                                    mutation.slice().projectId(), mutation.slice().batchDate(),
                                    mutation.slice().questionTier(), mutation.preservedResultIds(),
                                    mutation.metrics(), now));
                            String affectedRows = json.writeStableObject(
                                    Map.of("deletedRows", mutation.deletedRows()), false);
                            legacyIdentity.markSucceeded(legacyAuditId.get(), session.run().runId(),
                                    operation.operationId(), mutation.slice().projectId(),
                                    mutation.slice().batchDate(), mutation.slice().questionTier(),
                                    affectedRows, local(now));
                            RetentionMutationActual actual = new RetentionMutationActual(operation.operationId(),
                                    1, 1, 0, 0);
                            return new PendingMutation<>(mutation, actual, receipt);
                        });
                totals.addCommitted(committed.reportedActual(), cleanup);
                return;
            } catch (RuntimeException failure) {
                if (isUnknownOutcome(failure)) {
                    totals.stopped = true;
                    throw new DataRetentionExecutionException(503, "POLL_MUTATION_OUTCOME_UNKNOWN",
                            "Poll mutation commit outcome is unknown", failure);
                }
                ControlledStop controlled = mutationStarted.get() ? null : controlledStop(failure);
                if (controlled != null) {
                    completeNotStarted(session, attempt, controlled);
                    totals.stopped = true;
                    totals.controlledStopCode = controlled.resultCode();
                    totals.stopReason = controlled.stableCode();
                    return;
                }
                if (isTransient(failure) && transientRetries < MAX_TRANSIENT_SAME_SCOPE_RETRIES) {
                    transientRetries++;
                    continue;
                }
                if (legacyAuditId.get() != null) {
                    legacyAudit.markRetentionFailed(legacyAuditId.get(), session.run().runId(),
                            operation.operationId(), safeError(failure), local(now()));
                }
                completeKnownRollback(session, attempt, failure, transientRetries);
                totals.warnings++;
                totals.partial = true;
                if (!isKnownDataRollback(failure)) totals.stopped = true;
                return;
            }
        }
    }

    private void recoverRunning(RetentionExecutionSession session,
                                PollRecoveryOperation recovery,
                                Totals totals) {
        PollRetentionBusinessRepository.EvidenceState evidence = business.inspectEvidence(recovery.operation());
        Instant eventTime = now();
        int count = recovery.operation().itemCount();
        if ("exact".equals(recovery.markerState())
                && evidence == PollRetentionBusinessRepository.EvidenceState.POST) {
            session.audit().recoverAttempt(recovery.attempt().attemptId(), new RetentionAttemptResult(
                    AttemptStatus.SUCCEEDED, count, count, 0, 0,
                    Map.of("executionBudgetUsage", new ExecutionBudgetUsage(1, 1, count),
                            "recoveryEvidence", "MARKER_EXACT_POST"), null, null, eventTime));
            totals.candidates += count;
            totals.affected += count;
            totals.stopped = true;
            return;
        }
        if ("absent".equals(recovery.markerState())
                && evidence == PollRetentionBusinessRepository.EvidenceState.PRE) {
            session.audit().recoverAttempt(recovery.attempt().attemptId(), new RetentionAttemptResult(
                    AttemptStatus.FAILED, 0, 0, 0, 1,
                    Map.of("executionBudgetUsage", new ExecutionBudgetUsage(1, 0, 0),
                            "failureDisposition", "KNOWN_ROLLBACK",
                            "recoveryEvidence", "MARKER_ABSENT_PRE"),
                    "POLL_MUTATION_KNOWN_ROLLBACK", "Poll mutation remained fully PRE", eventTime));
            totals.warnings++;
            totals.partial = true;
            return;
        }
        session.audit().recoverAttempt(recovery.attempt().attemptId(), new RetentionAttemptResult(
                AttemptStatus.MANUAL_REVIEW, 0, 0, 0, 1,
                Map.of("executionBudgetUsage", new ExecutionBudgetUsage(1, 0, 0),
                        "recoveryEvidence", recovery.markerState().toUpperCase() + "_" + evidence.name()),
                "POLL_RECOVERY_EVIDENCE_CONFLICT", "Poll recovery evidence requires manual review", eventTime));
        totals.warnings++;
        totals.partial = true;
        totals.stopped = true;
    }

    private void completeNotStarted(RetentionExecutionSession session,
                                    RetentionAttemptContext attempt,
                                    ControlledStop controlled) {
        session.audit().recoverAttempt(attempt.attemptId(), new RetentionAttemptResult(
                AttemptStatus.SKIPPED, 0, 0, 0, 0,
                Map.of("failureDisposition", "CONTROLLED_STOP_BEFORE_MUTATION",
                        "controlledStopCode", controlled.stableCode(),
                        "executionBudgetUsage", new ExecutionBudgetUsage(1, 0, 0)),
                "recovery_not_started", "Poll mutation checkpoint rejected execution before start", now()));
    }

    private void completeKnownRollback(RetentionExecutionSession session,
                                       RetentionAttemptContext attempt,
                                       RuntimeException failure,
                                       int transientRetries) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("failureDisposition", isKnownDataRollback(failure) ? "DATA_CONTINUE" : "SAFETY_STOP");
        metrics.put("transientRetryCount", transientRetries);
        metrics.put("executionBudgetUsage", new ExecutionBudgetUsage(1, 0, 0));
        session.audit().recoverAttempt(attempt.attemptId(), new RetentionAttemptResult(
                AttemptStatus.FAILED, 0, 0, 0, 1, metrics, stableFailureCode(failure), safeError(failure), now()));
    }

    private RequestPolicy requestPolicy(RetentionExecutionSession session) {
        Map<String, Object> scope = session.command().scope();
        Object configRaw = scope.get("effectiveConfig");
        LocalDate effectiveStart = scopeDate(scope, "effectiveStartDate", false);
        LocalDate effectiveEnd = scopeDate(scope, "effectiveEndDate", true);
        if (!(configRaw instanceof Map<?, ?> config)
                || !(config.get("hotRetentionDays") instanceof Number hotDays)
                || !(config.get("stuckBatchSealDays") instanceof Number stuckDays)
                || !(scope.get("requestLimit") instanceof Number requestLimit)
                || hotDays.intValue() < 14 || stuckDays.intValue() < 1
                || requestLimit.intValue() < 1 || requestLimit.intValue() > 20
                || effectiveStart != null && effectiveStart.isAfter(effectiveEnd)
                || !Objects.equals(session.command().retentionWindowStart(), effectiveStart)
                || !Objects.equals(session.command().retentionWindowEnd(), effectiveEnd)) {
            throw new DataRetentionExecutionException(400, "POLL_SELECTION_INVALID",
                    "Poll execute policy is invalid");
        }
        if (session.command().source() == ExecutionSource.CANARY && requestLimit.intValue() > 1) {
            throw new DataRetentionExecutionException(400, "POLL_SELECTION_INVALID",
                    "Poll canary limit must be one");
        }
        Long projectId = number(scope.get("projectId"));
        String tier = scope.get("questionTier") instanceof String value ? value : null;
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return new RequestPolicy(hotDays.intValue(), stuckDays.intValue(), requestLimit.intValue(),
                effectiveEnd, today,
                new PollRetentionBusinessRepository.SelectionPolicy(projectId,
                        effectiveStart, effectiveEnd, tier));
    }

    private int cleanupLimit(RetentionExecutionSession session) {
        long remaining = session.command().budget().maxCandidateItems() - session.progress().usedCandidateItems();
        return (int) Math.max(0, Math.min(MAX_CLEANUP_RESULTS, remaining));
    }

    private static LocalDate scopeDate(Map<String, Object> scope, String key, boolean required) {
        Object value = scope.get(key);
        if (value == null) {
            if (required) throw invalidPolicy("Poll " + key + " is missing");
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidPolicy("Poll " + key + " is malformed");
        }
        try {
            return LocalDate.parse(text);
        } catch (java.time.format.DateTimeParseException malformed) {
            throw invalidPolicy("Poll " + key + " is malformed");
        }
    }

    private static DataRetentionExecutionException invalidPolicy(String message) {
        return new DataRetentionExecutionException(400, "POLL_SELECTION_INVALID", message);
    }

    private PollPurgeRequest legacyRequest(RetentionExecutionSession session,
                                           RetentionOperationContext operation) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("projectId", operation.pollProjectId());
        scope.put("batchDate", operation.pollBatchDate().toString());
        scope.put("questionTier", operation.pollQuestionTier());
        scope.put("retentionRunId", session.run().runId());
        scope.put("operationId", operation.operationId());
        return new PollPurgeRequest(operation.pollProjectId(), session.command().requestedBy(),
                session.command().reason(), json.writeStableObject(scope, false));
    }

    private static RetentionAttemptCommand attemptCommand(AttemptType type, String schemaKey) {
        return new RetentionAttemptCommand(UUID.randomUUID().toString(), type, null, null, Map.of(schemaKey, 1));
    }

    private static RetentionOperationContext withAttempt(RetentionOperationContext operation,
                                                         RetentionAttemptContext attempt,
                                                         List<Long> itemIds) {
        return new RetentionOperationContext(operation.operationId(), operation.originRunId(),
                operation.operationType(), operation.batchKeyHash(), operation.hashSchemaVersion(),
                operation.itemCount(), operation.websiteCutoff(), operation.upperBoundBusinessKey(),
                operation.pollProjectId(), operation.pollBatchDate(), operation.pollQuestionTier(),
                operation.pollRetentionCutoffDate(), operation.compatibilityContract(),
                operation.operationCreatedByVersion(), operation.currentWriterVersion(), operation.replayStatus(),
                "pending", attempt, itemIds);
    }

    private static boolean isUnknownOutcome(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DataRetentionExecutionException execution
                    && "MUTATION_OUTCOME_UNKNOWN".equals(execution.resultCode())) return true;
        }
        return false;
    }

    private static boolean isKnownDataRollback(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DataRetentionExecutionException execution
                    && List.of("POLL_SLICE_STATE_CHANGED", "POLL_POST_CHECK_FAILED")
                    .contains(execution.resultCode())) return true;
        }
        return false;
    }

    private static boolean isTransient(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof CannotAcquireLockException
                    || current instanceof PessimisticLockingFailureException
                    || current instanceof ConcurrencyFailureException
                    || current instanceof TransientDataAccessException) return true;
            if (current instanceof SQLException sql && ("40001".equals(sql.getSQLState())
                    || sql.getErrorCode() == 1205 || sql.getErrorCode() == 1213)) return true;
        }
        return false;
    }

    private static ControlledStop controlledStop(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DataRetentionExecutionException execution) {
                ExecutionResultCode code = switch (execution.resultCode()) {
                    case "EXECUTE_BUDGET_EXHAUSTED" -> ExecutionResultCode.EXECUTE_BUDGET_EXHAUSTED;
                    case "EXECUTE_STATIC_GLOBAL_DISABLED" -> ExecutionResultCode.EXECUTE_STATIC_GLOBAL_DISABLED;
                    case "EXECUTE_STATIC_DOMAIN_DISABLED" -> ExecutionResultCode.EXECUTE_STATIC_DOMAIN_DISABLED;
                    case "EXECUTE_LEASE_LOST" -> ExecutionResultCode.EXECUTE_LEASE_LOST;
                    default -> null;
                };
                if (code != null) return new ControlledStop(code, execution.resultCode());
            }
            if (current instanceof DynamicSwitchException dynamic) {
                return new ControlledStop(ExecutionResultCode.EXECUTE_DYNAMIC_DISABLED, dynamic.resultCode());
            }
        }
        return null;
    }

    private static String stableFailureCode(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DataRetentionExecutionException execution) return execution.resultCode();
            if (current instanceof DataRetentionAuditException audit) return audit.getErrorCode();
        }
        return "POLL_MUTATION_KNOWN_ROLLBACK";
    }

    private static String safeError(Throwable failure) {
        String value = failure.getMessage();
        if (value == null || value.isBlank()) return "Poll mutation rolled back";
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static RetentionExecutionResult result(Totals totals) {
        if (totals.controlledStopCode != null) {
            return new RetentionExecutionResult(null, totals.controlledStopCode, totals.candidates,
                    totals.affected, totals.skipped, totals.warnings, totals.stopReason);
        }
        ExecutionResultCode code = totals.partial ? ExecutionResultCode.PARTIAL : ExecutionResultCode.COMPLETED;
        return new RetentionExecutionResult(null, code, totals.candidates, totals.affected,
                totals.skipped, totals.warnings, totals.stopped ? "poll_domain_stopped" : null);
    }

    private record RequestPolicy(int hotRetentionDays, int stuckBatchSealDays, int requestLimit,
                                 LocalDate cutoff, LocalDate today,
                                 PollRetentionBusinessRepository.SelectionPolicy selection) { }
    private record ControlledStop(ExecutionResultCode resultCode, String stableCode) { }

    private static final class Totals {
        private long candidates;
        private long primaryCandidates;
        private long primarySelections;
        private long affected;
        private long skipped;
        private long warnings;
        private boolean partial;
        private boolean stopped;
        private ExecutionResultCode controlledStopCode;
        private String stopReason;

        private void addCommitted(RetentionMutationActual actual, boolean cleanup) {
            candidates += actual.candidateCount();
            affected += actual.affectedCount();
            skipped += actual.skippedCount();
            warnings += actual.warningCount();
            if (!cleanup) primaryCandidates += actual.candidateCount();
        }
    }
}
