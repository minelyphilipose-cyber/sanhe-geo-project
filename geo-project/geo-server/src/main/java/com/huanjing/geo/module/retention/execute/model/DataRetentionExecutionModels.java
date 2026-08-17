package com.huanjing.geo.module.retention.execute.model;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.service.TrustedMutationReceipt;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RecoveryAttemptState;

public final class DataRetentionExecutionModels {

    private DataRetentionExecutionModels() {
    }

    public enum ExecutionSource {
        MANUAL("manual"), SCHEDULER("scheduler"), CANARY("canary"), RETRY("retry");
        private final String databaseValue;
        ExecutionSource(String databaseValue) { this.databaseValue = databaseValue; }
        public String databaseValue() { return databaseValue; }
    }

    public enum ExecutionResultCode {
        COMPLETED,
        PARTIAL,
        TERMINAL_REPLAY,
        EXECUTE_LEASE_BUSY,
        EXECUTE_LEASE_UNAVAILABLE,
        EXECUTE_STATIC_GLOBAL_DISABLED,
        EXECUTE_STATIC_DOMAIN_DISABLED,
        EXECUTE_DYNAMIC_DISABLED,
        EXECUTE_CONFIGURATION_INVALID,
        EXECUTE_BUDGET_EXHAUSTED,
        EXECUTE_LEASE_LOST,
        DOMAIN_EXECUTOR_UNAVAILABLE,
        RUN_FINALIZATION_UNCERTAIN,
        RECOVERY_IN_PROGRESS
    }

    public enum TransactionOutcome { COMMITTED, KNOWN_ROLLBACK, NO_MUTATION, UNKNOWN }

    public record ExecutionBudget(int maxMutationTransactions,
                                  long maxCandidateItems,
                                  Duration maxRunDuration) {
        public ExecutionBudget {
            if (maxMutationTransactions < 1 || maxCandidateItems < 1 || maxRunDuration == null
                    || maxRunDuration.isZero() || maxRunDuration.isNegative()) {
                throw new IllegalArgumentException("execution budget must be positive");
            }
        }
    }

    public record ExecutionProgress(long usedMutationTransactions,
                                    long usedCandidateItems,
                                    Instant deadline) {
        public ExecutionProgress {
            if (usedMutationTransactions < 0 || usedCandidateItems < 0 || deadline == null) {
                throw new IllegalArgumentException("invalid execution progress");
            }
        }

        public ExecutionProgress charge(com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ExecutionBudgetUsage usage) {
            return new ExecutionProgress(
                    Math.addExact(usedMutationTransactions, usage.committedTransactionCount()),
                    Math.addExact(usedCandidateItems, usage.chargedCandidateItems()), deadline);
        }
    }

    public record RetentionExecuteCommand(String triggerId,
                                          RetentionDomain domain,
                                          ExecutionSource source,
                                          long requestedBy,
                                          String reason,
                                          String approvalRef,
                                          String ticketRef,
                                          RetentionCompatibilityContract compatibilityContract,
                                          Map<String, Object> scope,
                                          LocalDate retentionWindowStart,
                                          LocalDate retentionWindowEnd,
                                          ExecutionBudget budget,
                                          String relatedOperationId) {
        public RetentionExecuteCommand {
            scope = scope == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(scope));
        }
    }

    public record RetentionReconcileCommand(String triggerId,
                                             RetentionDomain domain,
                                             long requestedBy,
                                             String reason,
                                             String approvalRef,
                                             String ticketRef,
                                             String operationId,
                                             String reconciliationId) {
    }

    public record ReconciliationResult(long runId,
                                       String reconciliationId,
                                       String status,
                                       String decision) {
    }

    public record MutationEstimate(long candidateCount) {
        public MutationEstimate {
            if (candidateCount < 0) throw new IllegalArgumentException("candidate count must not be negative");
        }
    }

    public record RetentionMutationActual(String operationId,
                                          long candidateCount,
                                          long affectedCount,
                                          long skippedCount,
                                          long warningCount) {
        public RetentionMutationActual {
            if (candidateCount < 0 || affectedCount < 0 || skippedCount < 0 || warningCount < 0) {
                throw new IllegalArgumentException("mutation counts must not be negative");
            }
        }
    }

    public record PendingMutation<T>(T value,
                                     RetentionMutationActual reportedActual,
                                     TrustedMutationReceipt trustedReceipt) {
    }

    public record CommittedMutation<T>(T value,
                                       RetentionMutationActual reportedActual,
                                       TrustedMutationReceipt trustedReceipt,
                                       Instant committedAt) {
    }

    public record RetentionExecutionResult(Long runId,
                                           ExecutionResultCode code,
                                           long candidateCount,
                                           long affectedCount,
                                           long skippedCount,
                                           long warningCount,
                                           String stopReason) {
        public static RetentionExecutionResult stopped(ExecutionResultCode code, String reason) {
            return new RetentionExecutionResult(null, code, 0, 0, 0, 0, reason);
        }
    }

    public record PersistedRunState(RetentionRunContext run,
                                    ExecutionBudget originalBudget,
                                    ExecutionProgress progress,
                                    boolean emptyTree,
                                    boolean allAttemptsTerminal,
                                    List<RecoveryAttemptState> attempts) {
        public PersistedRunState {
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
        }
    }
}
