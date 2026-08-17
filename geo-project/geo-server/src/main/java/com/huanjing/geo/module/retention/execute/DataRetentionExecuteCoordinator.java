package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunSnapshot;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunStartCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunRecoveryState;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AuditOnlyReconciliationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ReconciliationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunBusinessSummary;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchGate;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupException;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupPromotionGate;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionBudget;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionResultCode;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PersistedRunState;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecuteCommand;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecutionResult;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionReconcileCommand;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ReconciliationResult;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DataRetentionExecuteCoordinator {
    private final DataRetentionP0AuditService audit;
    private final DataRetentionDomainExecutorRegistry registry;
    private final DataRetentionExecutionAuthorizationPolicy authorization;
    private final DataRetentionStaticExecuteGate staticGate;
    private final DataRetentionDynamicSwitchGate dynamicGate;
    private final DataRetentionExecutionBudgetGate budgetGate;
    private final DataRetentionExecuteLease leaseProvider;
    private final DataRetentionProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;
    private final DataRetentionDetailCleanupPromotionGate detailCleanupGate;

    @Autowired
    public DataRetentionExecuteCoordinator(DataRetentionP0AuditService audit,
                                           DataRetentionDomainExecutorRegistry registry,
                                           DataRetentionExecutionAuthorizationPolicy authorization,
                                           DataRetentionStaticExecuteGate staticGate,
                                           DataRetentionDynamicSwitchGate dynamicGate,
                                           DataRetentionExecutionBudgetGate budgetGate,
                                           DataRetentionExecuteLease leaseProvider,
                                           DataRetentionProperties properties,
                                           PlatformTransactionManager transactionManager,
                                           DataRetentionDetailCleanupPromotionGate detailCleanupGate) {
        this(audit, registry, authorization, staticGate, dynamicGate, budgetGate, leaseProvider,
                properties, transactionManager, detailCleanupGate, Clock.systemUTC());
    }

    public DataRetentionExecuteCoordinator(DataRetentionP0AuditService audit,
                                           DataRetentionDomainExecutorRegistry registry,
                                           DataRetentionExecutionAuthorizationPolicy authorization,
                                           DataRetentionStaticExecuteGate staticGate,
                                           DataRetentionDynamicSwitchGate dynamicGate,
                                           DataRetentionExecutionBudgetGate budgetGate,
                                           DataRetentionExecuteLease leaseProvider,
                                           DataRetentionProperties properties,
                                           PlatformTransactionManager transactionManager) {
        this(audit, registry, authorization, staticGate, dynamicGate, budgetGate, leaseProvider,
                properties, transactionManager, null, Clock.systemUTC());
    }

    DataRetentionExecuteCoordinator(DataRetentionP0AuditService audit,
                                    DataRetentionDomainExecutorRegistry registry,
                                    DataRetentionExecutionAuthorizationPolicy authorization,
                                    DataRetentionStaticExecuteGate staticGate,
                                    DataRetentionDynamicSwitchGate dynamicGate,
                                    DataRetentionExecutionBudgetGate budgetGate,
                                    DataRetentionExecuteLease leaseProvider,
                                    DataRetentionProperties properties,
                                    PlatformTransactionManager transactionManager,
                                    Clock clock) {
        this(audit, registry, authorization, staticGate, dynamicGate, budgetGate, leaseProvider,
                properties, transactionManager, null, clock);
    }

    DataRetentionExecuteCoordinator(DataRetentionP0AuditService audit,
                                    DataRetentionDomainExecutorRegistry registry,
                                    DataRetentionExecutionAuthorizationPolicy authorization,
                                    DataRetentionStaticExecuteGate staticGate,
                                    DataRetentionDynamicSwitchGate dynamicGate,
                                    DataRetentionExecutionBudgetGate budgetGate,
                                    DataRetentionExecuteLease leaseProvider,
                                    DataRetentionProperties properties,
                                    PlatformTransactionManager transactionManager,
                                    DataRetentionDetailCleanupPromotionGate detailCleanupGate,
                                    Clock clock) {
        this.audit = audit; this.registry = registry; this.authorization = authorization;
        this.staticGate = staticGate; this.dynamicGate = dynamicGate; this.budgetGate = budgetGate;
        this.leaseProvider = leaseProvider; this.properties = properties;
        this.transactionManager = transactionManager; this.clock = clock;
        this.detailCleanupGate = detailCleanupGate;
    }

    public RetentionExecutionResult execute(RetentionExecuteCommand raw) {
        RetentionExecuteCommand command = validate(raw);
        authorization.requireOperator(command.requestedBy(), command.source());
        RetentionRunStartCommand start = startCommand(command);
        RetentionRunSnapshot existing = audit.lookupRun(start);
        if (existing != null && existing.context().replayStatus()
                == com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus.TERMINAL_REPLAY) {
            return terminal(existing);
        }
        if (command.source() == ExecutionSource.CANARY) requireDetailCleanupReady();
        if (existing != null) {
            return resume(command, registry.require(command.domain()), existing.context().runId(), true);
        }
        DataRetentionDomainExecutor executor = registry.require(command.domain());
        staticGate.requireEnabled(command.domain(), command.source());
        budgetGate.preflight(command.budget());
        DataRetentionExecuteLease.AcquireResult acquired = leaseProvider.tryAcquire();
        if (acquired.code() == DataRetentionExecuteLease.AcquireCode.BUSY) {
            RetentionRunSnapshot concurrent = audit.lookupRun(start);
            if (concurrent != null && concurrent.context().replayStatus()
                    == com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus.TERMINAL_REPLAY) {
                return terminal(concurrent);
            }
            if (command.source() == ExecutionSource.SCHEDULER && concurrent == null) {
                RetentionRunContext skipped = audit.recordSkippedRun(start, "lease_busy");
                return new RetentionExecutionResult(skipped.runId(), ExecutionResultCode.EXECUTE_LEASE_BUSY,
                        0, 0, 0, 0, "lease_busy");
            }
            throw new DataRetentionExecutionException(409, "EXECUTE_LEASE_BUSY",
                    "Another data retention execution owns the global lease");
        }
        if (acquired.code() == DataRetentionExecuteLease.AcquireCode.UNAVAILABLE) {
            throw new DataRetentionExecutionException(503, "EXECUTE_LEASE_UNAVAILABLE",
                    "Data retention execute lease is unavailable", acquired.error());
        }
        try (DataRetentionExecuteLease.Lease lease = acquired.lease()) {
            lease.ensureHeld();
            dynamicGate.requireEnabled(command.domain());
            staticGate.requireEnabled(command.domain(), command.source());
            RetentionRunContext run = audit.startRun(start);
            if (run.replayStatus()
                    == com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus.TERMINAL_REPLAY) {
                RetentionRunSnapshot snapshot = audit.lookupRun(start);
                return terminal(Objects.requireNonNull(snapshot));
            }
            if (run.replayStatus()
                    == com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus.RUNNING_REPLAY) {
                return resumeWithLease(command, executor, run.runId(), lease);
            }
            DefaultRetentionExecutionSession session = new DefaultRetentionExecutionSession(
                    run, command, lease, staticGate, dynamicGate, budgetGate, audit, transactionManager,
                    properties.getExecute().getMutationTimeoutSeconds(),
                    budgetGate.initial(run.startedAt(), command.budget()), clock);
            RetentionExecutionResult result = executor.executeFresh(session);
            finish(run.runId(), result);
            return withRun(run.runId(), result);
        }
    }

    public RetentionExecutionResult recoverScheduler(long runId, long currentOperatorId) {
        RetentionRunRecoveryState state = audit.readRecoveryState(runId);
        if (state == null || state.snapshot().context().replayStatus()
                == com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus.TERMINAL_REPLAY) {
            if (state == null) throw new DataRetentionExecutionException(404, "EXECUTE_COMMAND_INVALID", "run not found");
            authorization.requireOperator(currentOperatorId, ExecutionSource.SCHEDULER);
            return terminal(state.snapshot());
        }
        if (!"scheduler".equals(state.snapshot().context().triggerSource())) {
            throw new DataRetentionExecutionException(409, "RECOVERABLE_RUN_CONFLICT",
                    "run is not a scheduler execution");
        }
        authorization.requireOperator(currentOperatorId, ExecutionSource.SCHEDULER);
        RetentionExecuteCommand command = commandFrom(state.snapshot().exactCommand(), ExecutionSource.SCHEDULER);
        return resume(command, registry.require(command.domain()), runId, false);
    }

    public RetentionExecutionResult recoverDeferredRetry(long runId, long currentOperatorId) {
        RetentionRunRecoveryState state = audit.readRecoveryState(runId);
        if (state == null) {
            throw new DataRetentionExecutionException(404, "EXECUTE_COMMAND_INVALID", "run not found");
        }
        RetentionRunStartCommand exact = state.snapshot().exactCommand();
        requireDeferredRetryProvenance(exact);
        authorization.requireDeferredRetryOperator(currentOperatorId);
        if (state.snapshot().context().replayStatus()
                == com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus.TERMINAL_REPLAY) {
            return terminal(state.snapshot());
        }
        RetentionExecuteCommand command = commandFrom(exact, ExecutionSource.RETRY);
        return resume(command, registry.require(command.domain()), runId, false);
    }

    public ReconciliationResult reconcile(RetentionReconcileCommand raw) {
        RetentionReconcileCommand command = validateReconcile(raw);
        authorization.requireReconcileOperator(command.requestedBy());
        AuditOnlyReconciliationCommand auditCommand = registry.require(command.domain())
                .investigateReconciliation(command);
        requireMatchingInvestigation(command, auditCommand);
        ReconciliationContext recorded = audit.recordAuditOnlyReconciliation(auditCommand);
        if (recorded.runContext() != null
                && recorded.runContext().replayStatus()
                != com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus.TERMINAL_REPLAY) {
            audit.finishRun(recorded.runId(), new RunBusinessSummary(0, 0, 0, 0, Map.of()));
        }
        return new ReconciliationResult(recorded.runId(), recorded.reconciliationId(),
                recorded.status(), recorded.decision());
    }

    private RetentionExecutionResult resume(RetentionExecuteCommand command,
                                            DataRetentionDomainExecutor executor,
                                            long runId,
                                            boolean requireMutationGates) {
        if (requireMutationGates) {
            staticGate.requireEnabled(command.domain(), command.source());
            dynamicGate.requireEnabled(command.domain());
        }
        budgetGate.preflight(command.budget());
        DataRetentionExecuteLease.AcquireResult acquired = leaseProvider.tryAcquire();
        if (acquired.code() == DataRetentionExecuteLease.AcquireCode.BUSY) {
            throw new DataRetentionExecutionException(409, "RECOVERY_IN_PROGRESS", "run recovery lease is busy");
        }
        if (acquired.code() == DataRetentionExecuteLease.AcquireCode.UNAVAILABLE) {
            throw new DataRetentionExecutionException(503, "EXECUTE_LEASE_UNAVAILABLE",
                    "run recovery lease is unavailable", acquired.error());
        }
        try (DataRetentionExecuteLease.Lease lease = acquired.lease()) {
            return resumeWithLease(command, executor, runId, lease);
        }
    }

    private RetentionExecutionResult resumeWithLease(RetentionExecuteCommand command,
                                                     DataRetentionDomainExecutor executor,
                                                     long runId,
                                                     DataRetentionExecuteLease.Lease lease) {
        RetentionRunRecoveryState state = audit.readRecoveryState(runId);
        if (state == null) throw new DataRetentionExecutionException(404, "EXECUTE_COMMAND_INVALID", "run not found");
        if (state.snapshot().context().replayStatus()
                == com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus.TERMINAL_REPLAY) {
            return terminal(state.snapshot());
        }
        ExecutionBudget original = budgetFrom(state.snapshot().scope());
        Instant deadline = state.snapshot().context().startedAt().plus(original.maxRunDuration());
        com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionProgress progress =
                new com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionProgress(
                        state.usedMutationTransactions(), state.usedCandidateItems(), deadline);
        DefaultRetentionExecutionSession session = new DefaultRetentionExecutionSession(
                state.snapshot().context(), command, lease, staticGate, dynamicGate, budgetGate, audit, transactionManager,
                properties.getExecute().getMutationTimeoutSeconds(), progress, clock);
        PersistedRunState persisted = new PersistedRunState(state.snapshot().context(), original, progress,
                state.emptyTree(), state.allAttemptsTerminal(), state.attempts());
        RetentionExecutionResult result = executor.resumeRunning(session, persisted);
        finish(runId, result);
        return withRun(runId, result);
    }

    private void finish(long runId, RetentionExecutionResult result) {
        RetentionExecutionResult safe = Objects.requireNonNull(result, "domain result");
        audit.finishRun(runId, new RunBusinessSummary(safe.candidateCount(), safe.affectedCount(),
                safe.skippedCount(), safe.warningCount(), Map.of()));
    }

    private void requireDetailCleanupReady() {
        if (detailCleanupGate == null) return;
        final ReadinessEvaluation evaluation;
        try { evaluation=detailCleanupGate.evaluateAndConverge(); }
        catch (DataRetentionDetailCleanupException error) {
            throw new DataRetentionExecutionException(503,error.code(),
                    "WP8 fail-closed convergence did not reach a trusted terminal state",error);
        }
        if (evaluation.ready()) return;
        int status = DataRetentionDetailCleanupPromotionGate.infrastructure(evaluation.primaryCode()) ? 503 : 409;
        throw new DataRetentionExecutionException(status, evaluation.primaryCode().name(),
                "WP8 detail cleanup readiness blocks canary execution");
    }

    private RetentionExecuteCommand validate(RetentionExecuteCommand command) {
        if (command == null || command.domain() == null || command.source() == null
                || command.compatibilityContract() == null || command.budget() == null
                || command.requestedBy() <= 0 || blank(command.reason())) invalid("execute command is incomplete");
        String trigger = command.triggerId() == null ? "" : command.triggerId().trim();
        try {
            if (!UUID.fromString(trigger).toString().equals(trigger)) invalid("triggerId must be canonical UUID");
        } catch (IllegalArgumentException error) { invalid("triggerId must be canonical UUID"); }
        if (!command.compatibilityContract().domain().equals(command.domain().databaseValue())) {
            invalid("domain and compatibility contract do not match");
        }
        boolean refRequired = command.source() != ExecutionSource.SCHEDULER;
        if (refRequired && blank(command.approvalRef()) && blank(command.ticketRef())) {
            invalid("manual/canary/retry requires approval or ticket reference");
        }
        if (command.source() == ExecutionSource.RETRY) {
            if (blank(command.relatedOperationId())) invalid("retry requires related operation");
        } else if (!blank(command.relatedOperationId())) invalid("origin command cannot carry related operation");
        return command;
    }

    private RetentionReconcileCommand validateReconcile(RetentionReconcileCommand command) {
        if (command == null || command.domain() == null || command.requestedBy() <= 0
                || blank(command.reason()) || blank(command.operationId()) || blank(command.reconciliationId())
                || (blank(command.approvalRef()) && blank(command.ticketRef()))) {
            throw new DataRetentionExecutionException(400, "RECONCILIATION_COMMAND_INVALID",
                    "reconciliation command is incomplete");
        }
        canonicalUuid(command.triggerId(), "triggerId");
        canonicalUuid(command.operationId(), "operationId");
        canonicalUuid(command.reconciliationId(), "reconciliationId");
        return command;
    }

    private void requireMatchingInvestigation(RetentionReconcileCommand command,
                                              AuditOnlyReconciliationCommand investigated) {
        if (investigated == null || investigated.runStart() == null
                || !command.operationId().equals(investigated.operationId())
                || !command.reconciliationId().equals(investigated.reconciliationId())
                || command.requestedBy() != investigated.requestedBy()
                || !command.triggerId().equals(investigated.runStart().triggerId())
                || !"reconcile".equals(investigated.runStart().triggerSource())
                || !"execute".equals(investigated.runStart().mode())
                || investigated.runStart().compatibilityContract() == null
                || !command.domain().databaseValue().equals(
                        investigated.runStart().compatibilityContract().domain())) {
            throw new DataRetentionExecutionException(503, "RECONCILIATION_EVIDENCE_INVALID",
                    "domain reconciliation evidence does not match the trusted command");
        }
    }

    private void requireDeferredRetryProvenance(RetentionRunStartCommand exact) {
        Map<String, Object> scope = exact.scope() == null ? Map.of() : exact.scope();
        Object raw = scope.get("executionInvocation");
        if (!"retry".equals(exact.triggerSource()) || !(raw instanceof Map<?, ?> invocation)
                || !"scheduler_deferred_retry".equals(invocation.get("kind"))
                || !(invocation.get("relatedOperationId") instanceof String operationId)
                || !(invocation.get("originRunId") instanceof Number originRunId)
                || originRunId.longValue() <= 0
                || !"recovery_not_started".equals(exact.requestReason())
                || !Objects.equals("retention-recovery:" + operationId, exact.ticketRef())
                || exact.approvalRef() != null) {
            throw new DataRetentionExecutionException(409, "DEFERRED_RETRY_RUN_CONFLICT",
                    "run is not a server-side deferred retry");
        }
    }

    private RetentionRunStartCommand startCommand(RetentionExecuteCommand command) {
        Map<String, Object> scope = new LinkedHashMap<>(command.scope());
        scope.put("configFingerprintSchemaVersion",
                command.compatibilityContract().configFingerprintSchemaVersion());
        scope.put("configFingerprint",
                DataRetentionCanonicalEncoder.hex(command.compatibilityContract().configFingerprint()));
        ExecutionBudget budget = command.budget();
        scope.put("executionBudget", Map.of(
                "schemaVersion", 1,
                "maxMutationTransactions", budget.maxMutationTransactions(),
                "maxCandidateItems", budget.maxCandidateItems(),
                "maxRunDurationSeconds", budget.maxRunDuration().toSeconds()));
        return new RetentionRunStartCommand(command.triggerId(), command.source().databaseValue(), "execute",
                command.requestedBy(), command.reason(), command.approvalRef(), command.ticketRef(),
                command.compatibilityContract(), scope, command.retentionWindowStart(), command.retentionWindowEnd(),
                clock.instant().truncatedTo(ChronoUnit.MILLIS));
    }

    @SuppressWarnings("unchecked")
    private RetentionExecuteCommand commandFrom(RetentionRunStartCommand exact, ExecutionSource source) {
        Map<String, Object> scope = exact.scope() == null ? Map.of() : exact.scope();
        ExecutionBudget budget = budgetFrom(scope);
        com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain domain =
                java.util.Arrays.stream(
                        com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain.values())
                        .filter(value -> value.databaseValue().equals(exact.compatibilityContract().domain()))
                        .findFirst().orElseThrow(() -> new DataRetentionExecutionException(503,
                                "EXECUTE_COMMAND_INVALID", "stored run domain is unsupported"));
        String related = null;
        Object invocation = scope.get("executionInvocation");
        if (invocation instanceof Map<?, ?> map && map.get("relatedOperationId") instanceof String value) related = value;
        return new RetentionExecuteCommand(exact.triggerId(), domain, source, exact.requestedBy(),
                exact.requestReason(), exact.approvalRef(), exact.ticketRef(), exact.compatibilityContract(),
                scope, exact.retentionWindowStart(), exact.retentionWindowEnd(), budget, related);
    }

    private ExecutionBudget budgetFrom(Map<String, Object> scope) {
        Object raw = scope == null ? null : scope.get("executionBudget");
        if (!(raw instanceof Map<?, ?> map)) {
            throw new DataRetentionExecutionException(503, "BUDGET_EVIDENCE_INVALID",
                    "stored run budget is missing");
        }
        try {
            int transactions = Math.toIntExact(number(map.get("maxMutationTransactions")));
            long candidates = number(map.get("maxCandidateItems"));
            long seconds = number(map.get("maxRunDurationSeconds"));
            if (number(map.get("schemaVersion")) != 1) throw new ArithmeticException();
            return new ExecutionBudget(transactions, candidates, java.time.Duration.ofSeconds(seconds));
        } catch (RuntimeException error) {
            throw new DataRetentionExecutionException(503, "BUDGET_EVIDENCE_INVALID",
                    "stored run budget is invalid", error);
        }
    }

    private static long number(Object value) {
        if (!(value instanceof Number number) || number.longValue() < 1
                || number.doubleValue() != number.longValue()) throw new ArithmeticException();
        return number.longValue();
    }

    private static RetentionExecutionResult terminal(RetentionRunSnapshot snapshot) {
        return new RetentionExecutionResult(snapshot.context().runId(), ExecutionResultCode.TERMINAL_REPLAY,
                snapshot.candidateCount(), snapshot.affectedCount(), snapshot.skippedCount(), snapshot.warningCount(), null);
    }

    private static RetentionExecutionResult withRun(long runId, RetentionExecutionResult result) {
        return new RetentionExecutionResult(runId, result.code(), result.candidateCount(), result.affectedCount(),
                result.skippedCount(), result.warningCount(), result.stopReason());
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void canonicalUuid(String value, String label) {
        try {
            if (value == null || !UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            throw new DataRetentionExecutionException(400, "RECONCILIATION_COMMAND_INVALID",
                    label + " must be a canonical UUID");
        }
    }
    private static void invalid(String message) {
        throw new DataRetentionExecutionException(400, "EXECUTE_COMMAND_INVALID", message);
    }
}
