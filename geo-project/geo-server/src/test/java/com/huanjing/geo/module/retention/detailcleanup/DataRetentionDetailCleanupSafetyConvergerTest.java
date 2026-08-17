package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessCode;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.Severity;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchDisablePort;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.ChangeStatus;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Payload;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Snapshot;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotIntegrity;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchRedisStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRetentionDetailCleanupSafetyConvergerTest {
    @Test
    void trustedCanonicalVectorMatchesApprovedContract() {
        String canonical = DataRetentionDetailCleanupSafetyConverger.trustedCanonical(
                "ELIGIBLE_ATTEMPT_BACKLOG_EXCEEDED",
                "11111111-1111-4111-8111-111111111111", 42);

        assertThat(UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8)).toString())
                .isEqualTo("2defe299-fcd6-3e86-8e04-de2e7921aa09");
        assertThat(canonical).endsWith("\n");
    }

    @Test
    void trustedAndUntrustedBasisCannotCollide() {
        String trusted = DataRetentionDetailCleanupSafetyConverger.trustedCanonical(
                "DETAIL_INVARIANT_VIOLATION", "11111111-1111-4111-8111-111111111111", 42);
        String untrusted = DataRetentionDetailCleanupSafetyConverger.untrustedCanonical(
                "DETAIL_INVARIANT_VIOLATION", "11111111-1111-4111-8111-111111111111", 42);

        assertThat(UUID.nameUUIDFromBytes(trusted.getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(UUID.nameUUIDFromBytes(untrusted.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aLaterManualTrueIdentityProducesAnotherDisableIdOnTheSameDay() {
        String first=DataRetentionDetailCleanupSafetyConverger.trustedCanonical(
                "DETAIL_INVARIANT_VIOLATION","11111111-1111-4111-8111-111111111111",42);
        String later=DataRetentionDetailCleanupSafetyConverger.trustedCanonical(
                "DETAIL_INVARIANT_VIOLATION","22222222-2222-4222-8222-222222222222",44);
        assertThat(UUID.nameUUIDFromBytes(first.getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(UUID.nameUUIDFromBytes(later.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void falseThenManualTrueOnSameDayExecutesASecondDistinctDisable() {
        DataRetentionDynamicSwitchRedisStore redis=mock(DataRetentionDynamicSwitchRedisStore.class);
        DataRetentionDynamicSwitchChangeRepository changes=mock(DataRetentionDynamicSwitchChangeRepository.class);
        DataRetentionDynamicSwitchDisablePort disable=mock(DataRetentionDynamicSwitchDisablePort.class);
        DataRetentionProperties properties=new DataRetentionProperties();
        properties.getDynamicSwitch().setReconcileOperatorUserId(7);
        when(changes.findRequested(1000)).thenReturn(List.of());
        when(redis.read(SwitchKey.GLOBAL_EXECUTE)).thenReturn(
                snapshot("11111111-1111-4111-8111-111111111111",42),
                snapshot("22222222-2222-4222-8222-222222222222",44));
        DataRetentionDynamicSwitchChangeRepository.ChangeRow applied=
                mock(DataRetentionDynamicSwitchChangeRepository.ChangeRow.class);
        when(applied.status()).thenReturn(ChangeStatus.APPLIED);
        when(disable.disable(any(),any(),anyLong(),any(),any(),any())).thenReturn(applied);
        DataRetentionDetailCleanupSafetyConverger converger=new DataRetentionDetailCleanupSafetyConverger(
                redis,changes,disable,properties,mock(DataRetentionDetailCleanupAlertSink.class));
        ReadinessEvaluation blocker=new ReadinessEvaluation(ReadinessCode.DETAIL_INVARIANT_VIOLATION,
                Severity.P0_DISABLE_GLOBAL,List.of(ReadinessCode.DETAIL_INVARIANT_VIOLATION),
                0,0,null,0L,0L);

        converger.converge(blocker);
        converger.converge(blocker);

        ArgumentCaptor<String> ids=ArgumentCaptor.forClass(String.class);
        verify(disable,org.mockito.Mockito.times(2)).disable(any(),ids.capture(),anyLong(),any(),any(),any());
        assertThat(ids.getAllValues()).doesNotHaveDuplicates();
    }

    private static Snapshot snapshot(String changeId,long version) {
        return new Snapshot(SwitchKey.GLOBAL_EXECUTE, SnapshotIntegrity.VALID,
                new Payload(changeId,version,true,Instant.parse("2026-08-15T00:00:00Z")),null);
    }
}
