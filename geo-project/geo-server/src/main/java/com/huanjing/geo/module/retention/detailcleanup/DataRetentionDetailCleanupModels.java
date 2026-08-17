package com.huanjing.geo.module.retention.detailcleanup;

import java.time.Instant;
import java.util.List;

public final class DataRetentionDetailCleanupModels {
    private DataRetentionDetailCleanupModels() { }

    public enum CandidateOutcome { PURGED, SKIPPED, WARNING }
    public enum UnknownCommitResolution { COMMITTED, ROLLED_BACK }
    public enum Severity { READY, BLOCK_ONLY, DISABLE_GLOBAL, P0_DISABLE_GLOBAL }

    public enum ReadinessCode {
        READY,
        MIGRATION_NOT_READY,
        CLEANUP_NEVER_SUCCEEDED,
        CLEANUP_SUCCESS_STALE_48H,
        CLEANUP_RUN_ACTIVE_STALE,
        DETAIL_INVARIANT_VIOLATION,
        ELIGIBLE_ATTEMPT_BACKLOG_EXCEEDED,
        ELIGIBLE_ITEM_BACKLOG_EXCEEDED,
        OLDEST_DETAIL_EXCEEDED_425D,
        RETENTION_CAPACITY_80_PERCENT,
        RETENTION_CAPACITY_90_PERCENT,
        FIVE_YEAR_PROJECTION_EXCEEDED,
        CAPACITY_HISTORY_INSUFFICIENT,
        READINESS_QUERY_FAILED
    }

    public record CandidateResult(CandidateOutcome outcome, long attempts, long items, String code) {
        public static CandidateResult purged(long attempts, long items) {
            return new CandidateResult(CandidateOutcome.PURGED, attempts, items, "PURGED");
        }
        public static CandidateResult skipped(String code) {
            return new CandidateResult(CandidateOutcome.SKIPPED, 0, 0, code);
        }
        public static CandidateResult warning(String code) {
            return new CandidateResult(CandidateOutcome.WARNING, 0, 0, code);
        }
    }

    public record UnknownCommitClassification(UnknownCommitResolution resolution,
                                              CandidateResult committedResult) {
        public static UnknownCommitClassification committed(long attempts, long items) {
            return new UnknownCommitClassification(UnknownCommitResolution.COMMITTED,
                    CandidateResult.purged(attempts, items));
        }
        public static UnknownCommitClassification rolledBack() {
            return new UnknownCommitClassification(UnknownCommitResolution.ROLLED_BACK, null);
        }
    }

    public record SweepSummary(long candidateOperations, long purgedOperations,
                               long skippedOperations, long warningCandidates,
                               long purgedOperationlessRuns, long purgedAttempts,
                               long purgedItems) { }

    public record ReadinessEvaluation(ReadinessCode primaryCode, Severity severity,
                                      List<ReadinessCode> codes, long eligibleAttempts,
                                      long eligibleItems, Instant oldestAnchor,
                                      Long retentionBytes, Long projectedFiveYearBytes) {
        public boolean ready() { return primaryCode == ReadinessCode.READY; }
        public boolean requiresDisable() {
            return severity == Severity.DISABLE_GLOBAL || severity == Severity.P0_DISABLE_GLOBAL;
        }
    }
}
