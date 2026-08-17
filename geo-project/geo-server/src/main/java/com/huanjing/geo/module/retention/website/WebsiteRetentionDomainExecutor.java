package com.huanjing.geo.module.retention.website;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ExecutionBudgetUsage;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RelationType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptResult;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionOperationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteCommitCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteLeafFailureCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteRetryScope;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteRecoveryOperation;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteSplitCommand;
import com.huanjing.geo.module.retention.audit.service.TrustedMutationReceipt;
import com.huanjing.geo.module.retention.audit.service.TrustedTerminalUsageReceipt;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditException;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class WebsiteRetentionDomainExecutor implements DataRetentionDomainExecutor {
    static final int MAX_TRANSIENT_SAME_SCOPE_RETRIES = 2;
    private final WebsiteRetentionBusinessRepository business;
    private final Clock clock;

    @Autowired
    public WebsiteRetentionDomainExecutor(WebsiteRetentionBusinessRepository business) {
        this(business, Clock.systemUTC());
    }

    WebsiteRetentionDomainExecutor(WebsiteRetentionBusinessRepository business, Clock clock) {
        this.business = business;
        this.clock = clock;
    }

    @Override
    public RetentionDomain domain() {
        return RetentionDomain.WEBSITE_PUBLISHED_CONTENT;
    }

    @Override
    public RetentionExecutionResult executeFresh(RetentionExecutionSession session) {
        RequestPolicy policy = requestPolicy(session);
        if (session.command().source() == ExecutionSource.RETRY) {
            return executeExactRetry(session, policy);
        }
        Instant cutoff = session.run().startedAt().minus(policy.retentionHours(), ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.MILLIS);
        WebsiteRetentionBusinessRepository.Selection initial = business.selectInitial(cutoff,
                Math.min(20, policy.requestLimit()));
        if (initial.articleIds().isEmpty()) {
            return completed(new Totals());
        }
        Totals totals = new Totals();
        long upperBound = initial.upperBound();
        List<Long> nextCandidates = initial.articleIds();
        while (!session.isStopped() && totals.candidates < policy.requestLimit()) {
            long remaining = remainingCandidates(session, policy, totals);
            if (remaining <= 0) break;
            if (nextCandidates.isEmpty()) {
                nextCandidates = business.selectCandidates(cutoff, upperBound, (int) Math.min(20, remaining));
                if (nextCandidates.isEmpty()) break;
            }
            int size = normalizedBatchSize(nextCandidates.size(), (int) Math.min(Integer.MAX_VALUE, remaining));
            List<Long> articleIds = List.copyOf(nextCandidates.subList(0, size));
            nextCandidates = List.of();
            AttemptType firstType = session.command().source() == ExecutionSource.CANARY
                    ? AttemptType.CANARY : AttemptType.NORMAL_BATCH;
            RetentionAttemptCommand firstAttempt = new RetentionAttemptCommand(
                    UUID.randomUUID().toString(), firstType, null, null,
                    Map.of("websiteBatchSchemaVersion", 1));
            RetentionOperationContext operation = session.audit().prepareWebsiteOrigin(session.run(),
                    new WebsiteOperationCommand(articleIds, cutoff, upperBound), firstAttempt);
            processTree(session, operation, operation.firstAttempt(), totals);
            if (totals.stopped) break;
        }
        return result(totals);
    }

    @Override
    public RetentionExecutionResult resumeRunning(RetentionExecutionSession session, PersistedRunState state) {
        if (state.emptyTree()) {
            return executeFresh(session);
        }
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
                throw new DataRetentionExecutionException(503, "WEBSITE_RECOVERY_EVIDENCE_CONFLICT",
                        "Website execute recovery contains an operationless attempt");
            }
            WebsiteRecoveryOperation recovery = session.audit().readWebsiteRecoveryOperation(
                    persisted.operationId(), session.run().runId(), persisted.attemptId());
            if (persisted.status() == AttemptStatus.PREPARED) {
                processTree(session, recovery.operation(), recovery.attempt(), totals);
                if (totals.stopped) break;
                continue;
            }
            recoverRunningAttempt(session, recovery, totals);
            if (totals.stopped) break;
        }
        return result(totals);
    }

    private void recoverRunningAttempt(RetentionExecutionSession session,
                                       WebsiteRecoveryOperation recovery,
                                       Totals totals) {
        WebsiteRetentionBusinessRepository.EvidenceState businessState =
                business.inspectEvidence(recovery.operation());
        int itemCount = recovery.operation().itemCount();
        Instant eventTime = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if ("exact".equals(recovery.markerState())
                && businessState == WebsiteRetentionBusinessRepository.EvidenceState.POST) {
            session.audit().recoverAttempt(recovery.attempt().attemptId(), new RetentionAttemptResult(
                    AttemptStatus.SUCCEEDED, itemCount, itemCount, 0, 0,
                    Map.of("executionBudgetUsage", new ExecutionBudgetUsage(1, 1, itemCount),
                            "recoveryEvidence", "MARKER_EXACT_POST"),
                    null, null, eventTime));
            totals.candidates += itemCount;
            totals.affected += itemCount;
            totals.stopped = true;
            return;
        }
        if ("absent".equals(recovery.markerState())
                && businessState == WebsiteRetentionBusinessRepository.EvidenceState.PRE) {
            session.audit().recoverAttempt(recovery.attempt().attemptId(), new RetentionAttemptResult(
                    AttemptStatus.FAILED, 0, 0, 0, 1,
                    Map.of("executionBudgetUsage", new ExecutionBudgetUsage(1, 0, 0),
                            "failureDisposition", "SAFETY_STOP",
                            "recoveryEvidence", "MARKER_ABSENT_PRE"),
                    "WEBSITE_BATCH_KNOWN_ROLLBACK",
                    "Running website attempt had no marker and remained fully PRE", eventTime));
            totals.warnings++;
            totals.partial = true;
            totals.stopped = true;
            return;
        }
        session.audit().recoverAttempt(recovery.attempt().attemptId(), new RetentionAttemptResult(
                AttemptStatus.MANUAL_REVIEW, 0, 0, 0, 1,
                Map.of("executionBudgetUsage", new ExecutionBudgetUsage(1, 0, 0),
                        "recoveryEvidence", recovery.markerState().toUpperCase() + "_" + businessState.name()),
                "WEBSITE_RECOVERY_EVIDENCE_CONFLICT",
                "Website recovery evidence requires manual review", eventTime));
        totals.warnings++;
        totals.partial = true;
        totals.stopped = true;
    }

    private RetentionExecutionResult executeExactRetry(RetentionExecutionSession session, RequestPolicy policy) {
        WebsiteRetryScope retryScope = session.audit().readWebsiteRetryScope(
                session.command().relatedOperationId());
        if (retryScope.articleIds().size() > policy.requestLimit()) {
            throw new DataRetentionExecutionException(409, "WEBSITE_SELECTION_INVALID",
                    "Exact retry scope exceeds the request limit");
        }
        RetentionAttemptCommand retryCommand = new RetentionAttemptCommand(
                UUID.randomUUID().toString(), AttemptType.RETRY, retryScope.latestAttemptId(), null,
                Map.of("websiteExactRetrySchemaVersion", 1));
        RetentionAttemptContext attempt = session.audit().prepareRelatedAttempt(session.run(),
                retryScope.operation().operationId(), RelationType.RETRY, retryCommand);
        RetentionOperationContext operation = new RetentionOperationContext(
                retryScope.operation().operationId(), retryScope.operation().originRunId(),
                retryScope.operation().operationType(), retryScope.operation().batchKeyHash(),
                retryScope.operation().hashSchemaVersion(), retryScope.operation().itemCount(),
                retryScope.operation().websiteCutoff(), retryScope.operation().upperBoundBusinessKey(),
                retryScope.operation().pollProjectId(), retryScope.operation().pollBatchDate(),
                retryScope.operation().pollQuestionTier(), retryScope.operation().pollRetentionCutoffDate(),
                retryScope.operation().compatibilityContract(), retryScope.operation().operationCreatedByVersion(),
                retryScope.operation().currentWriterVersion(), retryScope.operation().replayStatus(), "pending",
                attempt, retryScope.articleIds());
        Totals totals = new Totals();
        processTree(session, operation, attempt, totals);
        return result(totals);
    }

    private void processTree(RetentionExecutionSession session,
                             RetentionOperationContext root,
                             RetentionAttemptContext rootAttempt,
                             Totals totals) {
        Deque<OperationWork> queue = new ArrayDeque<>();
        queue.add(new OperationWork(root, rootAttempt));
        while (!queue.isEmpty() && !totals.stopped && !session.isStopped()) {
            OperationWork work = queue.removeFirst();
            BatchOutcome outcome = runMutation(session, work, totals);
            if (!outcome.children().isEmpty()) {
                for (RetentionOperationContext child : outcome.children()) {
                    queue.addLast(new OperationWork(child, child.firstAttempt()));
                }
            }
        }
    }

    private BatchOutcome runMutation(RetentionExecutionSession session,
                                     OperationWork work,
                                     Totals totals) {
        int transientRetries = 0;
        AtomicBoolean mutationStarted = new AtomicBoolean(false);
        while (true) {
            try {
                CommittedMutation<WebsiteRetentionBusinessRepository.MutationResult> committed =
                        session.inMutationTransaction(new MutationEstimate(work.operation().itemCount()), () -> {
                            if (mutationStarted.compareAndSet(false, true)) {
                                session.audit().markAttemptRunning(work.attempt().attemptId());
                            }
                            var locked = session.audit().lockWebsiteMutation(work.operation(),
                                    session.run().runId(), work.attempt().attemptId());
                            Instant businessTime = clock.instant().truncatedTo(ChronoUnit.MILLIS);
                            var mutation = business.mutate(locked, businessTime);
                            if (locked.activeFailureGroupId() != null) {
                                session.audit().resolveWebsiteFailureGroup(locked, session.command().requestedBy(),
                                        "exact_retry_committed", businessTime);
                            }
                            TrustedMutationReceipt receipt = session.audit().commitWebsiteMutation(
                                    new WebsiteCommitCommand(locked.operation(), session.run().runId(),
                                            work.attempt().attemptId(), businessTime, mutation.metrics()));
                            RetentionMutationActual actual = new RetentionMutationActual(
                                    work.operation().operationId(), work.operation().itemCount(),
                                    work.operation().itemCount(), 0, 0);
                            return new PendingMutation<>(mutation, actual, receipt);
                        });
                totals.addCommitted(committed.reportedActual());
                return BatchOutcome.none();
            } catch (RuntimeException failure) {
                if (isUnknownOutcome(failure)) {
                    totals.stopped = true;
                    throw new DataRetentionExecutionException(503, "WEBSITE_BATCH_OUTCOME_UNKNOWN",
                            "Website mutation commit outcome is unknown", failure);
                }
                ControlledStop controlledStop = mutationStarted.get() ? null : controlledStop(failure);
                if (controlledStop != null) {
                    completeNotStartedAttempt(session, work, controlledStop);
                    totals.stopped = true;
                    totals.controlledStopCode = controlledStop.resultCode();
                    totals.stopReason = controlledStop.stableCode();
                    return BatchOutcome.none();
                }
                RollbackDisposition disposition = classifyKnownRollback(failure);
                if (disposition == RollbackDisposition.TRANSIENT_SAME_SCOPE_RETRY) {
                    if (transientRetries < MAX_TRANSIENT_SAME_SCOPE_RETRIES) {
                        transientRetries++;
                        continue;
                    }
                    completeStoppedAttempt(session, work, "WEBSITE_TRANSIENT_RETRY_EXHAUSTED",
                            "TRANSIENT_SAME_SCOPE_RETRY", transientRetries);
                    totals.warnings++;
                    totals.partial = true;
                    totals.stopped = true;
                    return BatchOutcome.none();
                }
                if (disposition == RollbackDisposition.DATA_SPLIT_ELIGIBLE) {
                    totals.partial = true;
                    if (work.operation().itemCount() <= 5) {
                        TrustedTerminalUsageReceipt receipt = session.audit().openOrRefreshWebsiteLeafFailure(
                                new WebsiteLeafFailureCommand(work.operation(), session.run().runId(),
                                        work.attempt().attemptId(), stableFailureCode(failure),
                                        safeError(failure), transientRetries,
                                        clock.instant().truncatedTo(ChronoUnit.MILLIS)));
                        session.consumeTerminalUsage(receipt);
                        totals.candidates += work.operation().itemCount();
                        totals.skipped += work.operation().itemCount();
                        totals.warnings++;
                        return BatchOutcome.none();
                    }
                    var split = session.audit().finalizeWebsiteSplitParent(new WebsiteSplitCommand(
                            session.run(), work.operation(), work.attempt().attemptId(),
                            stableFailureCode(failure), safeError(failure), transientRetries,
                            clock.instant().truncatedTo(ChronoUnit.MILLIS)));
                    totals.warnings++;
                    return new BatchOutcome(split.children());
                }
                completeStoppedAttempt(session, work, "WEBSITE_ROLLBACK_DISPOSITION_UNCLASSIFIED",
                        "SAFETY_STOP", transientRetries);
                totals.warnings++;
                totals.partial = true;
                totals.stopped = true;
                return BatchOutcome.none();
            }
        }
    }

    private void completeNotStartedAttempt(RetentionExecutionSession session,
                                           OperationWork work,
                                           ControlledStop controlledStop) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("failureDisposition", "CONTROLLED_STOP_BEFORE_MUTATION");
        metrics.put("controlledStopCode", controlledStop.stableCode());
        metrics.put("executionBudgetUsage", new ExecutionBudgetUsage(1, 0, 0));
        session.audit().recoverAttempt(work.attempt().attemptId(), new RetentionAttemptResult(
                AttemptStatus.SKIPPED, 0, 0, 0, 0, metrics, "recovery_not_started",
                "Website mutation checkpoint rejected execution before the attempt started",
                clock.instant().truncatedTo(ChronoUnit.MILLIS)));
    }

    private void completeStoppedAttempt(RetentionExecutionSession session,
                                        OperationWork work,
                                        String errorCode,
                                        String disposition,
                                        int transientRetries) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("failureDisposition", disposition);
        metrics.put("transientRetryCount", transientRetries);
        metrics.put("executionBudgetUsage", new ExecutionBudgetUsage(1, 0, 0));
        session.audit().completeAttempt(work.attempt().attemptId(), new RetentionAttemptResult(
                AttemptStatus.FAILED, 0, 0, 0, 1, metrics, errorCode,
                "Website mutation stopped without a committed business transaction",
                clock.instant().truncatedTo(ChronoUnit.MILLIS)));
    }

    static int normalizedBatchSize(int available, int remaining) {
        int usable = Math.min(available, remaining);
        if (usable >= 20) return 20;
        if (usable >= 10) return 10;
        if (usable >= 5) return 5;
        if (usable >= 1) return usable;
        throw new DataRetentionExecutionException(400, "WEBSITE_SELECTION_INVALID",
                "Website selection did not contain a usable batch");
    }

    private long remainingCandidates(RetentionExecutionSession session, RequestPolicy policy, Totals totals) {
        long requestRemaining = policy.requestLimit() - totals.candidates;
        long budgetRemaining = session.command().budget().maxCandidateItems()
                - session.progress().usedCandidateItems();
        return Math.min(requestRemaining, budgetRemaining);
    }

    private RequestPolicy requestPolicy(RetentionExecutionSession session) {
        Map<String, Object> scope = session.command().scope();
        Object configRaw = scope.get("effectiveConfig");
        if (!(configRaw instanceof Map<?, ?> config)
                || !(config.get("retentionHours") instanceof Number retention)
                || !(scope.get("requestLimit") instanceof Number requestLimit)
                || retention.longValue() < 24 || retention.longValue() > Integer.MAX_VALUE
                || requestLimit.longValue() < 1 || requestLimit.longValue() > 1000) {
            throw new DataRetentionExecutionException(400, "WEBSITE_SELECTION_INVALID",
                    "Website execute policy is invalid");
        }
        int limit = Math.toIntExact(requestLimit.longValue());
        if (session.command().source() == ExecutionSource.CANARY && limit > 5) {
            throw new DataRetentionExecutionException(400, "WEBSITE_SELECTION_INVALID",
                    "Website canary limit must not exceed five");
        }
        return new RequestPolicy(Math.toIntExact(retention.longValue()), limit);
    }

    private static boolean isUnknownOutcome(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DataRetentionExecutionException execution
                    && "MUTATION_OUTCOME_UNKNOWN".equals(execution.resultCode())) return true;
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

    static RollbackDisposition classifyKnownRollback(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DataRetentionExecutionException execution
                    && List.of("WEBSITE_BATCH_STATE_CHANGED", "WEBSITE_POST_CHECK_FAILED")
                    .contains(execution.resultCode())) {
                return RollbackDisposition.DATA_SPLIT_ELIGIBLE;
            }
            if (current instanceof CannotAcquireLockException
                    || current instanceof PessimisticLockingFailureException
                    || current instanceof ConcurrencyFailureException
                    || current instanceof TransientDataAccessException) {
                return RollbackDisposition.TRANSIENT_SAME_SCOPE_RETRY;
            }
            if (current instanceof SQLException sql
                    && ("40001".equals(sql.getSQLState())
                    || sql.getErrorCode() == 1205 || sql.getErrorCode() == 1213)) {
                return RollbackDisposition.TRANSIENT_SAME_SCOPE_RETRY;
            }
        }
        return RollbackDisposition.SAFETY_STOP;
    }

    private static String stableFailureCode(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DataRetentionExecutionException execution) return execution.resultCode();
            if (current instanceof DataRetentionAuditException audit) return audit.getErrorCode();
        }
        return "WEBSITE_BATCH_KNOWN_ROLLBACK";
    }

    private static String safeError(Throwable failure) {
        String value = failure.getMessage();
        if (value == null || value.isBlank()) return "Website batch rolled back";
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private static RetentionExecutionResult completed(Totals totals) {
        return new RetentionExecutionResult(null, ExecutionResultCode.COMPLETED,
                totals.candidates, totals.affected, totals.skipped, totals.warnings, null);
    }

    private static RetentionExecutionResult result(Totals totals) {
        if (totals.controlledStopCode != null) {
            return new RetentionExecutionResult(null, totals.controlledStopCode,
                    totals.candidates, totals.affected, totals.skipped, totals.warnings, totals.stopReason);
        }
        if (!totals.partial) return completed(totals);
        return new RetentionExecutionResult(null, ExecutionResultCode.PARTIAL,
                totals.candidates, totals.affected, totals.skipped, totals.warnings,
                totals.stopped ? "website_domain_stopped" : "website_partial_disposition");
    }

    enum RollbackDisposition { TRANSIENT_SAME_SCOPE_RETRY, DATA_SPLIT_ELIGIBLE, SAFETY_STOP }
    private record RequestPolicy(int retentionHours, int requestLimit) { }
    private record OperationWork(RetentionOperationContext operation, RetentionAttemptContext attempt) { }
    private record ControlledStop(ExecutionResultCode resultCode, String stableCode) { }
    private record BatchOutcome(List<RetentionOperationContext> children) {
        private BatchOutcome { children = children == null ? List.of() : List.copyOf(children); }
        private static BatchOutcome none() { return new BatchOutcome(List.of()); }
    }

    private static final class Totals {
        private long candidates;
        private long affected;
        private long skipped;
        private long warnings;
        private boolean partial;
        private boolean stopped;
        private ExecutionResultCode controlledStopCode;
        private String stopReason;

        private void addCommitted(RetentionMutationActual actual) {
            candidates += actual.candidateCount();
            affected += actual.affectedCount();
            skipped += actual.skippedCount();
            warnings += actual.warningCount();
        }
    }
}
