package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessCode;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.Severity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataRetentionDetailCleanupReadinessServiceTest {
    private static final Instant NOW=Instant.parse("2026-08-15T00:00:00Z");
    private DataRetentionDetailCleanupRepository repository;
    private DataRetentionDetailCleanupRunRepository runs;
    private DataRetentionDetailCleanupCapacityService capacity;

    @BeforeEach void defaults() {
        repository=mock(DataRetentionDetailCleanupRepository.class);
        runs=mock(DataRetentionDetailCleanupRunRepository.class);
        capacity=mock(DataRetentionDetailCleanupCapacityService.class);
        when(repository.migrationReady()).thenReturn(true);
        when(repository.invariantViolationCount()).thenReturn(0L);
        when(repository.eligibleAttemptBacklog(NOW.minusSeconds(365L*86400))).thenReturn(0L);
        when(repository.eligibleItemBacklog(NOW.minusSeconds(365L*86400))).thenReturn(0L);
        when(repository.retentionTableBytes()).thenReturn(1000L);
        when(capacity.projectedFiveYearBytes(NOW,1000L)).thenReturn(2000L);
        when(runs.latestSucceeded()).thenReturn(new DataRetentionDetailCleanupRunRepository.RunEvidence(
                NOW.minusSeconds(60),0,0,null,1000L,2000L));
    }

    @Test void exactBacklogLimitsDoNotDisableButOneMoreDoes() {
        when(repository.eligibleAttemptBacklog(NOW.minusSeconds(365L*86400))).thenReturn(12_000L);
        when(repository.eligibleItemBacklog(NOW.minusSeconds(365L*86400))).thenReturn(96_000L);
        assertThat(service().evaluate().primaryCode()).isEqualTo(ReadinessCode.READY);
        when(repository.eligibleAttemptBacklog(NOW.minusSeconds(365L*86400))).thenReturn(12_001L);
        assertThat(service().evaluate().severity()).isEqualTo(Severity.DISABLE_GLOBAL);
    }

    @Test void hardAgeIsStrictlyOlderThan425Days() {
        when(repository.oldestUnpurgedAnchor()).thenReturn(NOW.minusSeconds(425L*86400));
        assertThat(service().evaluate().primaryCode()).isEqualTo(ReadinessCode.READY);
        when(repository.oldestUnpurgedAnchor()).thenReturn(NOW.minusSeconds(425L*86400).minusMillis(1));
        assertThat(service().evaluate().primaryCode()).isEqualTo(ReadinessCode.OLDEST_DETAIL_EXCEEDED_425D);
    }

    @Test void eightyAndNinetyPercentBoundariesHaveDifferentSeverity() {
        long limit=DataRetentionDetailCleanupReadinessService.CAPACITY_LIMIT;
        when(repository.retentionTableBytes()).thenReturn(limit*80/100);
        when(capacity.projectedFiveYearBytes(NOW,limit*80/100)).thenReturn(2000L);
        assertThat(service().evaluate().severity()).isEqualTo(Severity.BLOCK_ONLY);
        when(repository.retentionTableBytes()).thenReturn(limit*90/100);
        when(capacity.projectedFiveYearBytes(NOW,limit*90/100)).thenReturn(2000L);
        assertThat(service().evaluate().severity()).isEqualTo(Severity.DISABLE_GLOBAL);
    }

    @Test void exactly48HoursOldSuccessIsStale() {
        when(runs.latestSucceeded()).thenReturn(new DataRetentionDetailCleanupRunRepository.RunEvidence(
                NOW.minusSeconds(48*3600L),0,0,null,1000L,2000L));
        assertThat(service().evaluate().primaryCode()).isEqualTo(ReadinessCode.CLEANUP_SUCCESS_STALE_48H);
    }

    @Test void queryFailureIsFailClosed() {
        when(repository.migrationReady()).thenThrow(new IllegalStateException("metadata down"));
        assertThat(service().evaluate().primaryCode()).isEqualTo(ReadinessCode.READINESS_QUERY_FAILED);
    }

    private DataRetentionDetailCleanupReadinessService service() {
        return new DataRetentionDetailCleanupReadinessService(repository,runs,capacity,
                new DataRetentionDetailCleanupMetrics(new SimpleMeterRegistry()),new DataRetentionProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
