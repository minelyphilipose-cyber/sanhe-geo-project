package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.audit.support.DataRetentionApplicationVersionProvider;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.CandidateOutcome;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.CandidateResult;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.SweepSummary;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.UnknownCommitClassification;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.UnknownCommitResolution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

@Service
public class DataRetentionDetailCleanupService {
    private final DataRetentionDetailCleanupRepository repository;
    private final DataRetentionDetailCleanupRunRepository runs;
    private final DataRetentionDetailCleanupLease leaseProvider;
    private final DataRetentionDetailCleanupReadinessService readiness;
    private final DataRetentionDetailCleanupSafetyConverger converger;
    private final DataRetentionDetailCleanupAlertSink alerts;
    private final DataRetentionDetailCleanupCapacityService capacity;
    private final DataRetentionDetailCleanupMetrics metrics;
    private final DataRetentionApplicationVersionProvider versionProvider;
    private final DataRetentionProperties properties;
    private final Clock clock;

    @Autowired
    public DataRetentionDetailCleanupService(DataRetentionDetailCleanupRepository repository,
                                             DataRetentionDetailCleanupRunRepository runs,
                                             DataRetentionDetailCleanupLease leaseProvider,
                                             DataRetentionDetailCleanupReadinessService readiness,
                                             DataRetentionDetailCleanupSafetyConverger converger,
                                             DataRetentionDetailCleanupAlertSink alerts,
                                             DataRetentionDetailCleanupCapacityService capacity,
                                             DataRetentionDetailCleanupMetrics metrics,
                                             DataRetentionApplicationVersionProvider versionProvider,
                                             DataRetentionProperties properties) {
        this(repository, runs, leaseProvider, readiness, converger, alerts, capacity, metrics, versionProvider, properties, Clock.systemUTC());
    }

    DataRetentionDetailCleanupService(DataRetentionDetailCleanupRepository repository,
                                      DataRetentionDetailCleanupRunRepository runs,
                                      DataRetentionDetailCleanupLease leaseProvider,
                                      DataRetentionDetailCleanupReadinessService readiness,
                                      DataRetentionDetailCleanupSafetyConverger converger,
                                      DataRetentionDetailCleanupAlertSink alerts,
                                      DataRetentionDetailCleanupCapacityService capacity,
                                      DataRetentionDetailCleanupMetrics metrics,
                                      DataRetentionApplicationVersionProvider versionProvider,
                                      DataRetentionProperties properties, Clock clock) {
        this.repository=repository; this.runs=runs; this.leaseProvider=leaseProvider;
        this.readiness=readiness; this.converger=converger; this.alerts=alerts; this.capacity=capacity; this.metrics=metrics;
        this.versionProvider=versionProvider; this.properties=properties; this.clock=clock;
    }

    public RunResult runScheduled() {
        if (!properties.getDetailCleanup().isEnabled()) return new RunResult("disabled", null);
        return run("scheduler");
    }

    public RunResult runManualTest() { return run("manual_test"); }

    private RunResult run(String source) {
        validateLimits();
        DataRetentionDetailCleanupLease.AcquireResult acquired = leaseProvider.tryAcquire();
        if (acquired.code() == DataRetentionDetailCleanupLease.AcquireCode.BUSY) return new RunResult("busy", null);
        if (acquired.code() == DataRetentionDetailCleanupLease.AcquireCode.UNAVAILABLE) {
            alerts.alert("LEASE_UNAVAILABLE", acquired.error().getClass().getSimpleName());
            return new RunResult("lease_unavailable", null);
        }
        String runId = UUID.randomUUID().toString();
        Instant started = now();
        Instant cutoff = started.minus(365, ChronoUnit.DAYS);
        MutableSummary summary = new MutableSummary();
        try (DataRetentionDetailCleanupLease.Lease lease = acquired.lease()) {
            lease.ensureHeld();
            if (!runs.start(runId, source, versionProvider.currentVersion(), started, cutoff,
                    Math.min(properties.getDetailCleanup().getMaxRunSeconds(), 3600))) {
                return new RunResult("busy", null);
            }
            try {
                int operationAttemptLimit=Math.min(1000,properties.getDetailCleanup().getMaxAttemptsPerOperation());
                List<DataRetentionDetailCleanupRepository.OperationCandidate> operations = repository.findOperationCandidateRows(cutoff,
                        Math.min(20, properties.getDetailCleanup().getMaxOperationsPerRun()),operationAttemptLimit);
                summary.candidateOperations = operations.size();
                int operationlessAttemptLimit=Math.min(1000,properties.getDetailCleanup().getMaxOperationlessAttemptsPerRun());
                List<DataRetentionDetailCleanupRepository.OperationlessCandidate> operationless = repository.findOperationlessCandidateRows(cutoff,
                        Math.min(20, properties.getDetailCleanup().getMaxOperationlessRunsPerRun()),operationlessAttemptLimit);
                for (SweepCandidate candidate : fairOrder(operations,operationless)) {
                    checkpoint(lease, runId, started, summary);
                    if(candidate.operationless()) {
                        CandidateResult result=withKnownRollbackRetry(()->repository.purgeOperationlessRun(
                                runId,candidate.runId(),cutoff,operationlessAttemptLimit));
                        accept(summary,result,true);
                        if(result.outcome()==CandidateOutcome.PURGED) {
                            repository.advanceRunWaterline(candidate.runId(),cutoff);
                        }
                    } else {
                        CandidateResult result=purgeOperationWithOutcomeRecovery(runId,candidate.operationId(),cutoff,
                                operationAttemptLimit);
                        accept(summary,result,false);
                        if(result.outcome()==CandidateOutcome.PURGED) {
                            for(long relatedRunId:repository.findRunIdsForOperation(candidate.operationId())) {
                                repository.advanceRunWaterline(relatedRunId,cutoff);
                            }
                        }
                    }
                }
                if (repository.invariantViolationCount() != 0) {
                    throw new DataRetentionDetailCleanupException("DETAIL_INVARIANT_VIOLATION",
                            "permanent detail invariant sweep returned rows", true);
                }
                long attempts = repository.eligibleAttemptBacklog(cutoff);
                long items = repository.eligibleItemBacklog(cutoff);
                Instant oldest = repository.oldestUnpurgedAnchor();
                long bytes = repository.retentionTableBytes();
                Long projected = capacity.projectedIncludingCurrentSample(now(), bytes);
                runs.succeed(runId, now(), summary.snapshot(), attempts, items, oldest, bytes, projected);
                metrics.purged(summary.snapshot());
                if (bytes >= DataRetentionDetailCleanupReadinessService.CAPACITY_LIMIT * 60 / 100) {
                    alerts.alert("RETENTION_CAPACITY_60_PERCENT", "retention allowlist capacity reached warning level");
                }
                ReadinessEvaluation evaluation = readiness.evaluate();
                converger.converge(evaluation);
                metrics.run("succeeded");
                return new RunResult("succeeded", runId);
            } catch (RuntimeException error) {
                String code = error instanceof DataRetentionDetailCleanupException cleanup ? cleanup.code()
                        : "DETAIL_CLEANUP_INFRASTRUCTURE_FAILURE";
                runs.fail(runId, now(), code, error);
                metrics.run("failed");
                alerts.alert(code, error.getClass().getSimpleName());
                try {
                    ReadinessEvaluation evaluation = readiness.evaluate();
                    converger.converge(evaluation);
                } catch (RuntimeException convergenceError) {
                    alerts.alert("FAIL_CLOSED_CONVERGENCE_FAILED", convergenceError.getClass().getSimpleName());
                }
                return new RunResult("failed", runId);
            }
        }
    }

    private CandidateResult withKnownRollbackRetry(CandidateAction action) {
        try { return action.run(); }
        catch (CannotAcquireLockException | DeadlockLoserDataAccessException first) {
            try { return action.run(); }
            catch (CannotAcquireLockException | DeadlockLoserDataAccessException second) {
                return CandidateResult.warning("KNOWN_ROLLBACK");
            }
        }
    }

    private CandidateResult purgeOperationWithOutcomeRecovery(String runId, String operationId,
                                                                Instant cutoff, int maxAttempts) {
        CandidateAction action = () -> repository.purgeOperation(runId, operationId, cutoff, maxAttempts);
        try {
            return withKnownRollbackRetry(action);
        } catch (DataAccessResourceFailureException | RecoverableDataAccessException | TransactionSystemException unknown) {
            UnknownCommitClassification classification = repository.classifyOperationUnknownCommit(operationId);
            if (classification.resolution() == UnknownCommitResolution.COMMITTED) {
                return classification.committedResult();
            }
            return withKnownRollbackRetry(action);
        }
    }

    private void checkpoint(DataRetentionDetailCleanupLease.Lease lease, String runId,
                            Instant started, MutableSummary summary) {
        if (!now().isBefore(started.plusSeconds(Math.min(3600, properties.getDetailCleanup().getMaxRunSeconds())))) {
            throw new DataRetentionDetailCleanupException("DETAIL_CLEANUP_DEADLINE_EXCEEDED", "cleanup deadline exhausted");
        }
        lease.ensureHeld();
        runs.heartbeat(runId, now(), summary.snapshot());
    }

    private void accept(MutableSummary summary, CandidateResult result, boolean operationless) {
        if (result.outcome() == CandidateOutcome.PURGED) {
            if (operationless) summary.purgedOperationlessRuns++; else summary.purgedOperations++;
            summary.purgedAttempts += result.attempts(); summary.purgedItems += result.items();
        } else if (result.outcome() == CandidateOutcome.WARNING) {
            summary.warningCandidates++;
            metrics.warning(result.code(),operationless?"operationless":"operation");
        }
        else if (!operationless) summary.skippedOperations++;
    }

    private void validateLimits() {
        DataRetentionProperties.DetailCleanup cfg = properties.getDetailCleanup();
        if (cfg.getMaxOperationsPerRun() < 1 || cfg.getMaxOperationsPerRun() > 20
                || cfg.getMaxOperationlessRunsPerRun() < 1 || cfg.getMaxOperationlessRunsPerRun() > 20
                || cfg.getMaxAttemptsPerOperation() < 1 || cfg.getMaxAttemptsPerOperation() > 1000
                || cfg.getMaxOperationlessAttemptsPerRun() < 1 || cfg.getMaxOperationlessAttemptsPerRun() > 1000
                || cfg.getMaxRunSeconds() < 1 || cfg.getMaxRunSeconds() > 3600) {
            throw new DataRetentionDetailCleanupException("DETAIL_CLEANUP_CONFIG_INVALID",
                    "WP8 cleanup limits exceed the frozen safety contract");
        }
    }
    private static List<SweepCandidate> fairOrder(
            List<DataRetentionDetailCleanupRepository.OperationCandidate> operations,
            List<DataRetentionDetailCleanupRepository.OperationlessCandidate> operationless) {
        List<SweepCandidate> ordered=new ArrayList<>();
        if(!operations.isEmpty()) ordered.add(SweepCandidate.operation(operations.get(0)));
        if(!operationless.isEmpty()) ordered.add(SweepCandidate.operationless(operationless.get(0)));
        List<SweepCandidate> remainder=new ArrayList<>();
        operations.stream().skip(1).map(SweepCandidate::operation).forEach(remainder::add);
        operationless.stream().skip(1).map(SweepCandidate::operationless).forEach(remainder::add);
        remainder.sort(Comparator.comparing(SweepCandidate::anchor)
                .thenComparing(SweepCandidate::operationless)
                .thenComparing(SweepCandidate::stableId));
        ordered.addAll(remainder);
        return ordered;
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MILLIS); }
    @FunctionalInterface private interface CandidateAction { CandidateResult run(); }
    public record RunResult(String status, String cleanupRunId) { }
    private record SweepCandidate(boolean operationless,String operationId,Long runId,Instant anchor,String stableId) {
        static SweepCandidate operation(DataRetentionDetailCleanupRepository.OperationCandidate value){
            return new SweepCandidate(false,value.operationId(),null,value.effectiveAnchor(),value.operationId());
        }
        static SweepCandidate operationless(DataRetentionDetailCleanupRepository.OperationlessCandidate value){
            return new SweepCandidate(true,null,value.runId(),value.effectiveAnchor(),Long.toUnsignedString(value.runId()));
        }
    }
    private static final class MutableSummary {
        long candidateOperations, purgedOperations, skippedOperations, warningCandidates;
        long purgedOperationlessRuns, purgedAttempts, purgedItems;
        SweepSummary snapshot() { return new SweepSummary(candidateOperations,purgedOperations,skippedOperations,
                warningCandidates,purgedOperationlessRuns,purgedAttempts,purgedItems); }
    }
}
