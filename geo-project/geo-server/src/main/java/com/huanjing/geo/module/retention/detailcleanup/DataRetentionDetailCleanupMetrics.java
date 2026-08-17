package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.SweepSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.EnumMap;
import java.util.Map;

@Component
public class DataRetentionDetailCleanupMetrics {
    private final MeterRegistry registry;
    private final AtomicLong candidateOperations = new AtomicLong();
    private final AtomicLong backlogAttempts = new AtomicLong();
    private final AtomicLong backlogItems = new AtomicLong();
    private final AtomicLong oldestAgeDays = new AtomicLong();
    private final AtomicLong tableBytes = new AtomicLong();
    private final AtomicLong projectedBytes = new AtomicLong(-1);
    private final AtomicLong invariantViolations = new AtomicLong();
    private final AtomicLong lastSuccessAgeSeconds = new AtomicLong(-1);
    private final Map<DataRetentionDetailCleanupModels.ReadinessCode,AtomicLong> readinessValues=
            new EnumMap<>(DataRetentionDetailCleanupModels.ReadinessCode.class);

    public DataRetentionDetailCleanupMetrics(MeterRegistry registry) {
        this.registry=registry;
        registry.gauge("retention_detail_cleanup_candidate_operations", candidateOperations);
        registry.gauge("retention_detail_cleanup_eligible_attempt_backlog", backlogAttempts);
        registry.gauge("retention_detail_cleanup_eligible_item_backlog", backlogItems);
        registry.gauge("retention_detail_cleanup_oldest_age_days", oldestAgeDays);
        registry.gauge("retention_detail_cleanup_table_bytes", tableBytes);
        registry.gauge("retention_detail_cleanup_projected_five_year_bytes", projectedBytes);
        registry.gauge("retention_detail_cleanup_invariant_violations", invariantViolations);
        registry.gauge("retention_detail_cleanup_last_success_age_seconds",lastSuccessAgeSeconds);
        for(DataRetentionDetailCleanupModels.ReadinessCode code:DataRetentionDetailCleanupModels.ReadinessCode.values()){
            AtomicLong value=new AtomicLong();readinessValues.put(code,value);
            registry.gauge("retention_detail_cleanup_readiness",java.util.List.of(
                    io.micrometer.core.instrument.Tag.of("code",code.name())),value);
        }
    }

    public void run(String status) { registry.counter("retention_detail_cleanup_run_total", "status", status).increment(); }
    public void purged(SweepSummary summary) {
        candidateOperations.set(summary.candidateOperations());
        registry.counter("retention_detail_cleanup_purged_operations_total").increment(summary.purgedOperations());
        registry.counter("retention_detail_cleanup_purged_attempts_total").increment(summary.purgedAttempts());
        registry.counter("retention_detail_cleanup_purged_items_total").increment(summary.purgedItems());
    }
    public void warning(String code, String type) {
        registry.counter("retention_detail_cleanup_warning_candidates_total", "code", code, "type", type).increment();
    }
    public void readiness(ReadinessEvaluation evaluation, Instant now, long invariantCount) {
        backlogAttempts.set(evaluation.eligibleAttempts()); backlogItems.set(evaluation.eligibleItems());
        oldestAgeDays.set(evaluation.oldestAnchor()==null?0:
                Math.max(0,(now.toEpochMilli()-evaluation.oldestAnchor().toEpochMilli())/86_400_000));
        tableBytes.set(evaluation.retentionBytes()==null?0:evaluation.retentionBytes());
        projectedBytes.set(evaluation.projectedFiveYearBytes()==null?-1:evaluation.projectedFiveYearBytes());
        invariantViolations.set(invariantCount);
        readinessValues.values().forEach(value->value.set(0));
        readinessValues.get(evaluation.primaryCode()).set(1);
    }
    public void lastSuccessAge(Instant finishedAt,Instant now){
        lastSuccessAgeSeconds.set(finishedAt==null?-1:Math.max(0,(now.toEpochMilli()-finishedAt.toEpochMilli())/1000));
    }
}
