package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessCode;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.Severity;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupPromotionGate;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository.ChangeRow;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.ChangeStatus;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RequestSource;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import com.huanjing.geo.module.system.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRetentionDynamicSwitchWp8GateTest {
    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");
    private static final String CHANGE_ID = "20000000-0000-4000-8000-000000000001";

    private DataRetentionDynamicSwitchChangeRepository repository;
    private DataRetentionDynamicSwitchRedisStore redis;
    private DataRetentionDynamicSwitchAuthorizationPolicy authorization;
    private DataRetentionDetailCleanupPromotionGate gate;
    private DataRetentionDynamicSwitchManagementService service;

    @BeforeEach
    void setUp() {
        repository = mock(DataRetentionDynamicSwitchChangeRepository.class);
        redis = mock(DataRetentionDynamicSwitchRedisStore.class);
        authorization = mock(DataRetentionDynamicSwitchAuthorizationPolicy.class);
        gate = mock(DataRetentionDetailCleanupPromotionGate.class);
        SysUser user = new SysUser();
        user.setId(2L);
        user.setRole("admin");
        when(authorization.requireCurrent(anyString())).thenReturn(user);
        when(gate.evaluateAndConverge()).thenReturn(blocked());

        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getDynamicSwitch().setEnableWriteEnabled(true);
        service = new DataRetentionDynamicSwitchManagementService(repository, redis,
                mock(DataRetentionDynamicSwitchDisablePort.class), authorization,
                mock(DataRetentionDynamicSwitchAlertSink.class), properties,
                mock(DataRetentionDynamicSwitchMetrics.class), gate,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void targetTrueCreationIsRejectedBeforeRequestedAuditRow() {
        when(repository.findByChangeId(CHANGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createManual(SwitchKey.GLOBAL_EXECUTE,
                new DataRetentionDynamicSwitchManagementService.ManualChangeCommand(
                        CHANGE_ID, true, "enable", "CAB-1", null)))
                .isInstanceOf(DynamicSwitchException.class)
                .extracting(error -> ((DynamicSwitchException) error).resultCode())
                .isEqualTo(ReadinessCode.CLEANUP_NEVER_SUCCEEDED.name());

        verify(repository, never()).createRequested(any(), any());
    }

    @Test
    void approvalRechecksReadinessBeforeRedisEnableCas() {
        when(repository.findByChangeId(CHANGE_ID)).thenReturn(Optional.empty());
        when(redis.redisTime()).thenReturn(NOW);
        when(repository.approve(CHANGE_ID, 2L, "CAB-1", NOW)).thenReturn(approvedRequested());

        assertThatThrownBy(() -> service.approve(CHANGE_ID, "CAB-1"))
                .isInstanceOf(DynamicSwitchException.class)
                .extracting(error -> ((DynamicSwitchException) error).resultCode())
                .isEqualTo(ReadinessCode.CLEANUP_NEVER_SUCCEEDED.name());

        verify(redis, never()).enableCas(any(), any(), any(), any());
    }

    private static ReadinessEvaluation blocked() {
        return new ReadinessEvaluation(ReadinessCode.CLEANUP_NEVER_SUCCEEDED, Severity.BLOCK_ONLY,
                List.of(ReadinessCode.CLEANUP_NEVER_SUCCEEDED), 0, 0, null, 0L, null);
    }

    private static ChangeRow approvedRequested() {
        return new ChangeRow(1, CHANGE_ID, SwitchKey.GLOBAL_EXECUTE, 1, true,
                ChangeStatus.REQUESTED, NOW.minusSeconds(60), 1, RequestSource.MANUAL,
                "enable", "CAB-1", 2L, NOW, null, null, null,
                null, null, null, NOW.minusSeconds(60), NOW);
    }
}
