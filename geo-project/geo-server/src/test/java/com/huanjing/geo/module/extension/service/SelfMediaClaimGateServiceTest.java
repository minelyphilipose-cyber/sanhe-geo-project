package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.module.extension.config.SelfMediaRuntimeProperties;
import com.huanjing.geo.module.extension.dto.ClaimGateEvaluation;
import com.huanjing.geo.module.extension.dto.RuntimeReadinessQuery;
import com.huanjing.geo.module.extension.dto.RuntimeReadinessResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelfMediaClaimGateServiceTest {

    @Test
    void observeOnlyRecordsWouldBlockWithoutBlockingClaim() {
        SelfMediaRuntimeReadinessService readinessService = mock(SelfMediaRuntimeReadinessService.class);
        SelfMediaRuntimeProperties properties = new SelfMediaRuntimeProperties();
        RuntimeReadinessQuery query = new RuntimeReadinessQuery(10L, 99L, null, 20L, "toutiao", "claim", "fill");
        when(readinessService.evaluate(query)).thenReturn(RuntimeReadinessResult.blocked(
                List.of(SelfMediaRuntimeReadinessService.EXTENSION_NOT_SEEN),
                null,
                1L,
                30
        ));
        SelfMediaClaimGateService service = new SelfMediaClaimGateService(readinessService, properties);

        ClaimGateEvaluation evaluation = service.evaluate(query);

        assertTrue(evaluation.wouldBlock());
        assertFalse(evaluation.blockClaim());
        assertFalse(evaluation.markManualRequired());
    }

    @Test
    void brandPlatformModeHasPriorityOverGlobalMode() {
        SelfMediaRuntimeReadinessService readinessService = mock(SelfMediaRuntimeReadinessService.class);
        SelfMediaRuntimeProperties properties = new SelfMediaRuntimeProperties();
        properties.getGate().getGlobal().setMode(SelfMediaClaimGateService.MODE_BLOCK_NON_DESTRUCTIVE);
        SelfMediaRuntimeProperties.BrandRule brandRule = new SelfMediaRuntimeProperties.BrandRule();
        SelfMediaRuntimeProperties.GateRule brandPlatformRule = new SelfMediaRuntimeProperties.GateRule();
        brandPlatformRule.setMode(SelfMediaClaimGateService.MODE_OBSERVE_ONLY);
        brandRule.getPlatforms().put("toutiao", brandPlatformRule);
        properties.getGate().getBrands().put(10L, brandRule);
        RuntimeReadinessQuery query = new RuntimeReadinessQuery(10L, 99L, null, 20L, "toutiao", "claim", "fill");
        when(readinessService.evaluate(query)).thenReturn(RuntimeReadinessResult.blocked(
                List.of(SelfMediaRuntimeReadinessService.EXTENSION_NOT_SEEN),
                null,
                1L,
                30
        ));
        SelfMediaClaimGateService service = new SelfMediaClaimGateService(readinessService, properties);

        ClaimGateEvaluation evaluation = service.evaluate(query);

        assertTrue(evaluation.wouldBlock());
        assertFalse(evaluation.blockClaim());
    }

    @Test
    void browserEnvironmentDisabledIsTerminalReason() {
        SelfMediaRuntimeReadinessService readinessService = mock(SelfMediaRuntimeReadinessService.class);
        SelfMediaRuntimeProperties properties = new SelfMediaRuntimeProperties();
        properties.getGate().setDefaultMode(SelfMediaClaimGateService.MODE_MANUAL_REQUIRED_TERMINAL);
        RuntimeReadinessQuery query = new RuntimeReadinessQuery(10L, 99L, null, 20L, "toutiao", "claim", "fill");
        when(readinessService.evaluate(query)).thenReturn(RuntimeReadinessResult.blocked(
                List.of("BROWSER_ENVIRONMENT_DISABLED"),
                null,
                1L,
                30
        ));
        SelfMediaClaimGateService service = new SelfMediaClaimGateService(readinessService, properties);

        ClaimGateEvaluation evaluation = service.evaluate(query);

        assertTrue(evaluation.markManualRequired());
    }

    @Test
    void capabilityUnsupportedIsTerminalReason() {
        SelfMediaRuntimeReadinessService readinessService = mock(SelfMediaRuntimeReadinessService.class);
        SelfMediaRuntimeProperties properties = new SelfMediaRuntimeProperties();
        properties.getGate().setDefaultMode(SelfMediaClaimGateService.MODE_MANUAL_REQUIRED_TERMINAL);
        RuntimeReadinessQuery query = new RuntimeReadinessQuery(10L, 99L, null, 20L, "toutiao", "claim", "fill");
        when(readinessService.evaluate(query)).thenReturn(RuntimeReadinessResult.blocked(
                List.of(SelfMediaRuntimeReadinessService.EXTENSION_CAPABILITY_UNSUPPORTED),
                null,
                1L,
                30
        ));
        SelfMediaClaimGateService service = new SelfMediaClaimGateService(readinessService, properties);

        ClaimGateEvaluation evaluation = service.evaluate(query);

        assertTrue(evaluation.markManualRequired());
    }
}
