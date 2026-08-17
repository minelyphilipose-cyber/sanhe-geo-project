package com.huanjing.geo.module.retention.audit.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataRetentionAuditModels {

    public static final String WEBSITE_DOMAIN = "website_published_content";
    public static final String POLL_DOMAIN = "poll_results";

    private DataRetentionAuditModels() {
    }

    public enum RunReplayStatus {
        CREATED,
        RUNNING_REPLAY,
        TERMINAL_REPLAY
    }

    public enum OperationReplayStatus {
        CREATED,
        PREPARE_REPLAY,
        TERMINAL_REPLAY
    }

    public enum RelationType {
        ORIGIN("origin"),
        RETRY("retry"),
        RECONCILE("reconcile");

        private final String databaseValue;

        RelationType(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }
    }

    public enum AttemptStatus {
        PREPARED("prepared"),
        RUNNING("running"),
        SUCCEEDED("succeeded"),
        FAILED("failed"),
        SKIPPED("skipped"),
        UNKNOWN("unknown"),
        MANUAL_REVIEW("manual_review");

        private final String databaseValue;

        AttemptStatus(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public boolean terminal() {
            return this != PREPARED && this != RUNNING;
        }
    }

    public enum AttemptType {
        PAGE("page", false),
        NORMAL_BATCH("normal_batch", true),
        SPLIT("split", true),
        RETRY("retry", true),
        CANARY("canary", true),
        RECONCILE("reconcile", true),
        LEASE_BUSY("lease_busy", false);

        private final String databaseValue;
        private final boolean operationRequired;

        AttemptType(String databaseValue, boolean operationRequired) {
            this.databaseValue = databaseValue;
            this.operationRequired = operationRequired;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public boolean operationRequired() {
            return operationRequired;
        }
    }

    public record RetentionCompatibilityContract(
            String domain,
            int configFingerprintSchemaVersion,
            byte[] configFingerprint,
            String policyVersion) {

        public RetentionCompatibilityContract {
            configFingerprint = configFingerprint == null ? null : configFingerprint.clone();
        }

        @Override
        public byte[] configFingerprint() {
            return configFingerprint == null ? null : configFingerprint.clone();
        }
    }

    public record RetentionRunStartCommand(
            String triggerId,
            String triggerSource,
            String mode,
            long requestedBy,
            String requestReason,
            String approvalRef,
            String ticketRef,
            RetentionCompatibilityContract compatibilityContract,
            Map<String, Object> scope,
            LocalDate retentionWindowStart,
            LocalDate retentionWindowEnd,
            Instant startedAt) {
    }

    public record RetentionRunContext(
            long runId,
            String triggerId,
            String triggerSource,
            String mode,
            RetentionCompatibilityContract compatibilityContract,
            String runCreatedByVersion,
            String currentWriterVersion,
            RunReplayStatus replayStatus,
            String status,
            Instant startedAt) {
    }

    public record RetentionRunSnapshot(
            RetentionRunContext context,
            RetentionRunStartCommand exactCommand,
            long candidateCount,
            long affectedCount,
            long skippedCount,
            long warningCount,
            Map<String, Object> scope) {
        public RetentionRunSnapshot {
            scope = immutableNullableMap(scope);
        }
    }

    public record RetentionRunRecoveryState(
            RetentionRunSnapshot snapshot,
            long usedMutationTransactions,
            long usedCandidateItems,
            boolean emptyTree,
            boolean allAttemptsTerminal,
            List<RecoveryAttemptState> attempts) {
        public RetentionRunRecoveryState {
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
        }
    }

    public record RecoveryAttemptState(long attemptId,
                                       String operationId,
                                       AttemptType attemptType,
                                       AttemptStatus status,
                                       long candidateCount,
                                       long affectedCount,
                                       long skippedCount,
                                       long warningCount,
                                       ExecutionBudgetUsage usage) {
    }

    public record DeferredRetryOperation(
            String operationId,
            long originRunId,
            String domain,
            RetentionCompatibilityContract compatibilityContract,
            Map<String, Object> originScope) {
        public DeferredRetryOperation {
            originScope = immutableNullableMap(originScope);
        }
    }

    public record RetentionAttemptCommand(
            String attemptRequestId,
            AttemptType attemptType,
            Long expectedLatestAttemptId,
            Long lineageParentAttemptId,
            Map<String, Object> domainScope) {
    }

    public record RetentionAttemptContext(
            long attemptId,
            long runId,
            String operationId,
            int sequenceNo,
            int attemptNo,
            AttemptType attemptType,
            AttemptStatus status,
            String attemptRequestId) {
    }

    public record WebsiteOperationCommand(
            List<Long> articleIds,
            Instant cutoff,
            long upperBoundBusinessKey) {
    }

    public record PollOperationCommand(
            long projectId,
            LocalDate batchDate,
            String questionTier,
            LocalDate retentionCutoffDate) {
    }

    public record PollCleanupOperationCommand(
            String originOperationId,
            long projectId,
            LocalDate batchDate,
            String questionTier,
            LocalDate retentionCutoffDate,
            List<Long> pollResultIds) {
        public PollCleanupOperationCommand {
            pollResultIds = pollResultIds == null ? List.of() : List.copyOf(pollResultIds);
        }
    }

    public record RetentionOperationContext(
            String operationId,
            long originRunId,
            String operationType,
            byte[] batchKeyHash,
            int hashSchemaVersion,
            int itemCount,
            Instant websiteCutoff,
            Long upperBoundBusinessKey,
            Long pollProjectId,
            LocalDate pollBatchDate,
            String pollQuestionTier,
            LocalDate pollRetentionCutoffDate,
            RetentionCompatibilityContract compatibilityContract,
            String operationCreatedByVersion,
            String currentWriterVersion,
            OperationReplayStatus replayStatus,
            String outcomeStatus,
            RetentionAttemptContext firstAttempt,
            List<Long> operationItemIds) {

        public RetentionOperationContext {
            batchKeyHash = batchKeyHash == null ? null : batchKeyHash.clone();
            operationItemIds = operationItemIds == null ? List.of() : List.copyOf(operationItemIds);
        }

        @Override
        public byte[] batchKeyHash() {
            return batchKeyHash == null ? null : batchKeyHash.clone();
        }

        public List<Long> websiteArticleIds() {
            return operationItemIds;
        }

        public List<Long> pollCleanupResultIds() {
            return operationItemIds;
        }
    }

    public record RetentionAttemptResult(
            AttemptStatus status,
            long candidateCount,
            long affectedCount,
            long skippedCount,
            long warningCount,
            Map<String, Object> metrics,
            String stableErrorCode,
            String errorMessage,
            Instant eventTime) {
    }

    public record RunBusinessSummary(
            long candidateCount,
            long affectedCount,
            long skippedCount,
            long warningCount,
            Map<String, Object> metrics) {
    }

    public record ExecutionBudgetUsage(int schemaVersion,
                                       long committedTransactionCount,
                                       long chargedCandidateItems) {
        public ExecutionBudgetUsage {
            if (schemaVersion != 1 || committedTransactionCount < 0 || committedTransactionCount > 1
                    || chargedCandidateItems < 0) {
                throw new IllegalArgumentException("invalid execution budget usage");
            }
        }
    }

    public record EvidenceDocument(byte[] evidenceUtf8JsonBytes, byte[] sha256Digest) {
        public EvidenceDocument {
            evidenceUtf8JsonBytes = evidenceUtf8JsonBytes == null ? null : evidenceUtf8JsonBytes.clone();
            sha256Digest = sha256Digest == null ? null : sha256Digest.clone();
        }

        @Override
        public byte[] evidenceUtf8JsonBytes() {
            return evidenceUtf8JsonBytes == null ? null : evidenceUtf8JsonBytes.clone();
        }

        @Override
        public byte[] sha256Digest() {
            return sha256Digest == null ? null : sha256Digest.clone();
        }
    }

    public record ReconciliationPrepareCommand(
            String reconciliationId,
            RetentionRunContext run,
            String operationId,
            String markerState,
            String businessState,
            String decision,
            EvidenceDocument evidence,
            long requestedBy) {
    }

    public record ReconciliationResolutionCommand(
            String reconciliationId,
            String operationId,
            String decision,
            EvidenceDocument evidence,
            String resolutionReason,
            long resolvedBy,
            Instant resolvedAt) {
    }

    public record AuditOnlyReconciliationCommand(
            RetentionRunStartCommand runStart,
            String operationId,
            String reconciliationId,
            String markerState,
            String businessState,
            String decision,
            EvidenceDocument evidence,
            long requestedBy,
            String resolutionReason,
            long resolvedBy,
            Instant resolvedAt) {
    }

    public record ReconciliationContext(
            long databaseId,
            String reconciliationId,
            String operationId,
            long runId,
            String status,
            String decision,
            byte[] evidenceHash,
            RetentionRunContext runContext) {

        public ReconciliationContext {
            evidenceHash = evidenceHash == null ? null : evidenceHash.clone();
        }

        @Override
        public byte[] evidenceHash() {
            return evidenceHash == null ? null : evidenceHash.clone();
        }
    }

    public record WebsiteCommitCommand(
            RetentionOperationContext operation,
            long runId,
            long attemptId,
            Instant committedAt,
            Map<String, Object> metrics) {
        public WebsiteCommitCommand {
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        }

        public WebsiteCommitCommand(RetentionOperationContext operation,
                                    long runId,
                                    long attemptId,
                                    Instant committedAt) {
            this(operation, runId, attemptId, committedAt, Map.of());
        }
    }

    public record LockedWebsiteMutationContext(
            RetentionOperationContext operation,
            long runId,
            long attemptId,
            List<Long> articleIds,
            Long activeFailureGroupId) {

        public LockedWebsiteMutationContext {
            articleIds = articleIds == null ? List.of() : List.copyOf(articleIds);
        }
    }

    public record WebsiteLeafFailureCommand(
            RetentionOperationContext operation,
            long runId,
            long attemptId,
            String failureCode,
            String errorMessage,
            int transientRetryCount,
            Instant failedAt) {
    }

    public record WebsiteSplitCommand(
            RetentionRunContext run,
            RetentionOperationContext parentOperation,
            long parentAttemptId,
            String failureCode,
            String errorMessage,
            int transientRetryCount,
            Instant failedAt) {
    }

    public record WebsiteSplitResult(List<RetentionOperationContext> children) {
        public WebsiteSplitResult {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    public record WebsiteRetryScope(RetentionOperationContext operation,
                                    long latestAttemptId,
                                    List<Long> articleIds) {
        public WebsiteRetryScope {
            articleIds = articleIds == null ? List.of() : List.copyOf(articleIds);
        }
    }

    public record WebsiteRecoveryOperation(RetentionOperationContext operation,
                                           RetentionAttemptContext attempt,
                                           String markerState) {
    }

    public record LockedPollMutationContext(RetentionOperationContext operation,
                                            long runId,
                                            long attemptId,
                                            List<Long> pollResultIds) {
        public LockedPollMutationContext {
            pollResultIds = pollResultIds == null ? List.of() : List.copyOf(pollResultIds);
        }
    }

    public record PollRetryScope(RetentionOperationContext operation,
                                 long latestAttemptId,
                                 List<Long> pollResultIds) {
        public PollRetryScope {
            pollResultIds = pollResultIds == null ? List.of() : List.copyOf(pollResultIds);
        }
    }

    public record PollRecoveryOperation(RetentionOperationContext operation,
                                        RetentionAttemptContext attempt,
                                        String markerState) {
    }

    public record PollCommitCommand(
            RetentionOperationContext operation,
            long runId,
            long attemptId,
            long projectId,
            LocalDate batchDate,
            String questionTier,
            List<Long> preservedResultIds,
            Map<String, Object> metrics,
            Instant committedAt) {
        public PollCommitCommand {
            preservedResultIds = preservedResultIds == null ? List.of() : List.copyOf(preservedResultIds);
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        }

        public PollCommitCommand(RetentionOperationContext operation,
                                 long runId,
                                 long attemptId,
                                 long projectId,
                                 LocalDate batchDate,
                                 String questionTier,
                                 Map<String, Object> metrics,
                                 Instant committedAt) {
            this(operation, runId, attemptId, projectId, batchDate, questionTier,
                    List.of(), metrics, committedAt);
        }
    }

    public record PollCleanupCommitCommand(
            RetentionOperationContext operation,
            long runId,
            long attemptId,
            String originOperationId,
            List<Long> pollResultIds,
            Map<String, Object> metrics,
            Instant committedAt) {
        public PollCleanupCommitCommand {
            pollResultIds = pollResultIds == null ? List.of() : List.copyOf(pollResultIds);
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        }
    }

    private static <K, V> Map<K, V> immutableNullableMap(Map<K, V> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
