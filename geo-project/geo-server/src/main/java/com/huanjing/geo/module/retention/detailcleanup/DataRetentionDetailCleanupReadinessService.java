package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessCode;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.Severity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DataRetentionDetailCleanupReadinessService {
    static final long ATTEMPT_LIMIT = 12_000;
    static final long ITEM_LIMIT = 96_000;
    static final long CAPACITY_LIMIT = 5L * 1024 * 1024 * 1024;
    private final DataRetentionDetailCleanupRepository repository;
    private final DataRetentionDetailCleanupRunRepository runs;
    private final DataRetentionDetailCleanupCapacityService capacity;
    private final DataRetentionDetailCleanupMetrics metrics;
    private final DataRetentionProperties properties;
    private final Clock clock;

    @Autowired
    public DataRetentionDetailCleanupReadinessService(DataRetentionDetailCleanupRepository repository,
                                                       DataRetentionDetailCleanupRunRepository runs,
                                                       DataRetentionDetailCleanupCapacityService capacity,
                                                       DataRetentionDetailCleanupMetrics metrics,
                                                       DataRetentionProperties properties) {
        this(repository, runs, capacity, metrics, properties, Clock.systemUTC());
    }

    DataRetentionDetailCleanupReadinessService(DataRetentionDetailCleanupRepository repository,
                                                DataRetentionDetailCleanupRunRepository runs,
                                                DataRetentionDetailCleanupCapacityService capacity,
                                                DataRetentionDetailCleanupMetrics metrics,
                                                DataRetentionProperties properties, Clock clock) {
        this.repository = repository;
        this.runs = runs;
        this.capacity = capacity;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    public ReadinessEvaluation evaluate() {
        Instant now = clock.instant();
        try {
            if (!repository.migrationReady()) return decision(ReadinessCode.MIGRATION_NOT_READY,
                    Severity.BLOCK_ONLY, List.of(ReadinessCode.MIGRATION_NOT_READY), 0, 0, null, null, null);
            List<ReadinessCode> codes = new ArrayList<>();
            long invariantCount = repository.invariantViolationCount();
            Instant activeHeartbeat = runs.activeHeartbeat();
            DataRetentionDetailCleanupRunRepository.RunEvidence latest = runs.latestSucceeded();
            metrics.lastSuccessAge(latest==null?null:latest.finishedAt(),now);
            long attempts = repository.eligibleAttemptBacklog(now.minusSeconds(365L * 86400));
            long items = repository.eligibleItemBacklog(now.minusSeconds(365L * 86400));
            Instant oldest = repository.oldestUnpurgedAnchor();
            long bytes = repository.retentionTableBytes();
            Long projected = capacity.projectedFiveYearBytes(now, bytes);

            if (invariantCount > 0) codes.add(ReadinessCode.DETAIL_INVARIANT_VIOLATION);
            if (oldest != null && oldest.isBefore(now.minusSeconds(425L * 86400))) codes.add(ReadinessCode.OLDEST_DETAIL_EXCEEDED_425D);
            if (attempts > ATTEMPT_LIMIT) codes.add(ReadinessCode.ELIGIBLE_ATTEMPT_BACKLOG_EXCEEDED);
            if (items > ITEM_LIMIT) codes.add(ReadinessCode.ELIGIBLE_ITEM_BACKLOG_EXCEEDED);
            if (bytes >= percent(CAPACITY_LIMIT, 90)) codes.add(ReadinessCode.RETENTION_CAPACITY_90_PERCENT);
            else if (bytes >= percent(CAPACITY_LIMIT, 80)) codes.add(ReadinessCode.RETENTION_CAPACITY_80_PERCENT);
            if (projected == null) codes.add(ReadinessCode.CAPACITY_HISTORY_INSUFFICIENT);
            else if (projected > CAPACITY_LIMIT) codes.add(ReadinessCode.FIVE_YEAR_PROJECTION_EXCEEDED);
            long staleSeconds=Math.min(3600,properties.getDetailCleanup().getMaxRunSeconds())+60L;
            if (activeHeartbeat != null && !activeHeartbeat.isAfter(now.minusSeconds(staleSeconds))) codes.add(ReadinessCode.CLEANUP_RUN_ACTIVE_STALE);
            if (latest == null) codes.add(ReadinessCode.CLEANUP_NEVER_SUCCEEDED);
            else if (!latest.finishedAt().isAfter(now.minusSeconds(48 * 3600L))) codes.add(ReadinessCode.CLEANUP_SUCCESS_STALE_48H);
            if (codes.isEmpty()) codes.add(ReadinessCode.READY);
            ReadinessCode primary = primary(codes);
            Severity severity = severity(primary);
            ReadinessEvaluation evaluation = decision(primary, severity, List.copyOf(codes), attempts, items, oldest, bytes, projected);
            metrics.readiness(evaluation, now, invariantCount);
            return evaluation;
        } catch (RuntimeException error) {
            return decision(ReadinessCode.READINESS_QUERY_FAILED, Severity.BLOCK_ONLY,
                    List.of(ReadinessCode.READINESS_QUERY_FAILED), 0, 0, null, null, null);
        }
    }

    private static ReadinessCode primary(List<ReadinessCode> codes) {
        ReadinessCode[] order = {ReadinessCode.DETAIL_INVARIANT_VIOLATION,
                ReadinessCode.OLDEST_DETAIL_EXCEEDED_425D,
                ReadinessCode.ELIGIBLE_ATTEMPT_BACKLOG_EXCEEDED,
                ReadinessCode.ELIGIBLE_ITEM_BACKLOG_EXCEEDED,
                ReadinessCode.RETENTION_CAPACITY_90_PERCENT,
                ReadinessCode.RETENTION_CAPACITY_80_PERCENT,
                ReadinessCode.FIVE_YEAR_PROJECTION_EXCEEDED,
                ReadinessCode.CLEANUP_RUN_ACTIVE_STALE,
                ReadinessCode.CLEANUP_NEVER_SUCCEEDED,
                ReadinessCode.CLEANUP_SUCCESS_STALE_48H,
                ReadinessCode.CAPACITY_HISTORY_INSUFFICIENT,
                ReadinessCode.MIGRATION_NOT_READY,
                ReadinessCode.READINESS_QUERY_FAILED,
                ReadinessCode.READY};
        for (ReadinessCode code : order) if (codes.contains(code)) return code;
        return ReadinessCode.READINESS_QUERY_FAILED;
    }

    private static Severity severity(ReadinessCode code) {
        if (code == ReadinessCode.DETAIL_INVARIANT_VIOLATION) return Severity.P0_DISABLE_GLOBAL;
        return switch (code) {
            case OLDEST_DETAIL_EXCEEDED_425D, ELIGIBLE_ATTEMPT_BACKLOG_EXCEEDED,
                    ELIGIBLE_ITEM_BACKLOG_EXCEEDED, RETENTION_CAPACITY_90_PERCENT -> Severity.DISABLE_GLOBAL;
            case READY -> Severity.READY;
            default -> Severity.BLOCK_ONLY;
        };
    }

    private static long percent(long value, int percentage) { return Math.multiplyExact(value, percentage) / 100; }
    private static ReadinessEvaluation decision(ReadinessCode primary, Severity severity,
                                                List<ReadinessCode> codes, long attempts, long items,
                                                Instant oldest, Long bytes, Long projected) {
        return new ReadinessEvaluation(primary, severity, codes, attempts, items, oldest, bytes, projected);
    }
}
