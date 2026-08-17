package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessCode;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.Severity;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupPromotionGate;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchGate;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionBudget;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecuteCommand;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRetentionExecuteWp8CanaryGateTest {
    @Test
    void canaryIsRejectedBeforeRunAuditLeaseAndBudget() {
        DataRetentionP0AuditService audit = mock(DataRetentionP0AuditService.class);
        DataRetentionDomainExecutorRegistry registry = mock(DataRetentionDomainExecutorRegistry.class);
        DataRetentionExecutionAuthorizationPolicy authorization = mock(DataRetentionExecutionAuthorizationPolicy.class);
        DataRetentionStaticExecuteGate staticGate = mock(DataRetentionStaticExecuteGate.class);
        DataRetentionDynamicSwitchGate dynamicGate = mock(DataRetentionDynamicSwitchGate.class);
        DataRetentionExecutionBudgetGate budgetGate = mock(DataRetentionExecutionBudgetGate.class);
        DataRetentionExecuteLease lease = mock(DataRetentionExecuteLease.class);
        DataRetentionDetailCleanupPromotionGate wp8 = mock(DataRetentionDetailCleanupPromotionGate.class);
        when(wp8.evaluateAndConverge()).thenReturn(new ReadinessEvaluation(
                ReadinessCode.CLEANUP_SUCCESS_STALE_48H, Severity.BLOCK_ONLY,
                List.of(ReadinessCode.CLEANUP_SUCCESS_STALE_48H), 0, 0, null, 0L, null));

        DataRetentionExecuteCoordinator coordinator = new DataRetentionExecuteCoordinator(audit, registry,
                authorization, staticGate, dynamicGate, budgetGate, lease, new DataRetentionProperties(),
                mock(PlatformTransactionManager.class), wp8,
                Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> coordinator.execute(command()))
                .isInstanceOf(DataRetentionExecutionException.class)
                .extracting(error -> ((DataRetentionExecutionException) error).resultCode())
                .isEqualTo(ReadinessCode.CLEANUP_SUCCESS_STALE_48H.name());

        verify(audit, never()).startRun(any());
        verify(lease, never()).tryAcquire();
        verify(budgetGate, never()).preflight(any());
        verify(registry, never()).require(any());
    }

    private static RetentionExecuteCommand command() {
        DataRetentionCanonicalEncoder encoder = new DataRetentionCanonicalEncoder();
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                RetentionDomain.WEBSITE_PUBLISHED_CONTENT.databaseValue(), 1,
                encoder.websiteFingerprint(24).sha256(), DataRetentionCanonicalEncoder.WEBSITE_POLICY_VERSION);
        return new RetentionExecuteCommand("20000000-0000-4000-8000-000000000001",
                RetentionDomain.WEBSITE_PUBLISHED_CONTENT, ExecutionSource.CANARY, 7,
                "WP8 canary gate", "CAB-WP8", null, contract,
                Map.of("effectiveConfig", encoder.websiteEffectiveConfig(24)), null, null,
                new ExecutionBudget(1, 20, Duration.ofMinutes(5)), null);
    }
}
