package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.audit.support.DataRetentionApplicationVersionProvider;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.CandidateResult;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessCode;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.Severity;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.UnknownCommitClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionSystemException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRetentionDetailCleanupServiceTest {
    private static final String OPERATION_ID = "90000000-0000-0000-0000-000000000001";
    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

    private DataRetentionDetailCleanupRepository repository;
    private DataRetentionDetailCleanupRunRepository runs;
    private DataRetentionDetailCleanupLease leaseProvider;
    private DataRetentionDetailCleanupLease.Lease lease;
    private DataRetentionDetailCleanupReadinessService readiness;
    private DataRetentionDetailCleanupSafetyConverger converger;
    private DataRetentionDetailCleanupAlertSink alerts;
    private DataRetentionDetailCleanupCapacityService capacity;
    private DataRetentionDetailCleanupMetrics metrics;
    private DataRetentionApplicationVersionProvider versionProvider;
    private DataRetentionDetailCleanupService service;

    @BeforeEach
    void setUp() {
        repository = mock(DataRetentionDetailCleanupRepository.class);
        runs = mock(DataRetentionDetailCleanupRunRepository.class);
        leaseProvider = mock(DataRetentionDetailCleanupLease.class);
        lease = mock(DataRetentionDetailCleanupLease.Lease.class);
        readiness = mock(DataRetentionDetailCleanupReadinessService.class);
        converger = mock(DataRetentionDetailCleanupSafetyConverger.class);
        alerts = mock(DataRetentionDetailCleanupAlertSink.class);
        capacity = mock(DataRetentionDetailCleanupCapacityService.class);
        metrics = mock(DataRetentionDetailCleanupMetrics.class);
        versionProvider = mock(DataRetentionApplicationVersionProvider.class);

        when(leaseProvider.tryAcquire()).thenReturn(new DataRetentionDetailCleanupLease.AcquireResult(
                DataRetentionDetailCleanupLease.AcquireCode.ACQUIRED, lease, null));
        when(runs.start(anyString(), eq("manual_test"), anyString(), any(), any(), anyInt())).thenReturn(true);
        when(versionProvider.currentVersion()).thenReturn("wp8-test");
        when(repository.findOperationCandidateRows(any(), anyInt(), anyInt())).thenReturn(List.of(
                new DataRetentionDetailCleanupRepository.OperationCandidate(OPERATION_ID,
                        NOW.minusSeconds(366L * 86_400))));
        when(repository.findOperationlessCandidateRows(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(repository.oldestUnpurgedAnchor()).thenReturn(null);
        when(readiness.evaluate()).thenReturn(new ReadinessEvaluation(ReadinessCode.READY, Severity.READY,
                List.of(ReadinessCode.READY), 0, 0, null, 0L, 0L));

        DataRetentionProperties properties = new DataRetentionProperties();
        service = new DataRetentionDetailCleanupService(repository, runs, leaseProvider, readiness, converger,
                alerts, capacity, metrics, versionProvider, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void disabledSchedulerReturnsBeforeRedisAndMysql() {
        assertThat(service.runScheduled().status()).isEqualTo("disabled");
        verify(leaseProvider, never()).tryAcquire();
        verify(repository, never()).findOperationCandidateRows(any(), anyInt(), anyInt());
    }

    @Test
    void lostCommitAckUsesCommittedReplayWithoutDeletingAgain() {
        when(repository.purgeOperation(anyString(), eq(OPERATION_ID), any(), anyInt()))
                .thenThrow(new TransactionSystemException("lost commit acknowledgement"));
        when(repository.classifyOperationUnknownCommit(OPERATION_ID))
                .thenReturn(UnknownCommitClassification.committed(2, 1));

        assertThat(service.runManualTest().status()).isEqualTo("succeeded");

        verify(repository).purgeOperation(anyString(), eq(OPERATION_ID), any(), anyInt());
        verify(repository).classifyOperationUnknownCommit(OPERATION_ID);
        verify(metrics).purged(any());
    }

    @Test
    void classifiedRollbackAllowsOneFreshMutationAttempt() {
        when(repository.purgeOperation(anyString(), eq(OPERATION_ID), any(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("lost connection"))
                .thenReturn(CandidateResult.purged(1, 1));
        when(repository.classifyOperationUnknownCommit(OPERATION_ID))
                .thenReturn(UnknownCommitClassification.rolledBack());

        assertThat(service.runManualTest().status()).isEqualTo("succeeded");

        verify(repository, org.mockito.Mockito.times(2))
                .purgeOperation(anyString(), eq(OPERATION_ID), any(), anyInt());
    }

    @Test
    void operationlessCommitAdvancesWaterlineOnlyAfterThePurgeReturnsCommitted() {
        when(repository.findOperationCandidateRows(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(repository.findOperationlessCandidateRows(any(), anyInt(), anyInt())).thenReturn(List.of(
                new DataRetentionDetailCleanupRepository.OperationlessCandidate(301,
                        NOW.minusSeconds(366L * 86_400))));
        when(repository.purgeOperationlessRun(anyString(), eq(301L), any(), anyInt()))
                .thenReturn(CandidateResult.purged(1, 0));

        assertThat(service.runManualTest().status()).isEqualTo("succeeded");

        verify(repository).purgeOperationlessRun(anyString(), eq(301L), any(), anyInt());
        verify(repository).advanceRunWaterline(eq(301L), any());
    }

    @Test
    void operationCommitRetriesWaterlinesForEveryPermanentlyRelatedRun() {
        when(repository.purgeOperation(anyString(), eq(OPERATION_ID), any(), anyInt()))
                .thenReturn(CandidateResult.purged(2, 1));
        when(repository.findRunIdsForOperation(OPERATION_ID)).thenReturn(List.of(301L, 302L));

        assertThat(service.runManualTest().status()).isEqualTo("succeeded");

        verify(repository).findRunIdsForOperation(OPERATION_ID);
        verify(repository).advanceRunWaterline(eq(301L), any());
        verify(repository).advanceRunWaterline(eq(302L), any());
    }

    @Test
    void mixedUnknownStateFailsSweepAndDoesNotReplayDelete() {
        when(repository.purgeOperation(anyString(), eq(OPERATION_ID), any(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("lost commit acknowledgement"));
        when(repository.classifyOperationUnknownCommit(OPERATION_ID))
                .thenThrow(new DataRetentionDetailCleanupException(
                        "UNKNOWN_COMMIT_MIXED_STATE", "mixed", true));

        assertThat(service.runManualTest().status()).isEqualTo("failed");

        verify(repository).purgeOperation(anyString(), eq(OPERATION_ID), any(), anyInt());
        verify(runs).fail(anyString(), any(), eq("UNKNOWN_COMMIT_MIXED_STATE"), any());
        verify(metrics, never()).purged(any());
    }
}
