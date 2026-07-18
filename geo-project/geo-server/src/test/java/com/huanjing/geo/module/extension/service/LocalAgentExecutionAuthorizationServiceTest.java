package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalAgentExecutionAuthorizationServiceTest {

    private static final long OPERATOR_ID = 13L;
    private static final long SESSION_ID = 5L;
    private static final long BRAND_ID = 15L;
    private static final long ENVIRONMENT_ID = 21L;

    private LocalAgentSessionMapper sessionMapper;
    private LocalAgentRuntimeStatusMapper runtimeStatusMapper;
    private SelfMediaPublishScheduleMapper scheduleMapper;
    private BrandAccessService brandAccessService;
    private LocalAgentExecutionAuthorizationService service;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(LocalAgentSessionMapper.class);
        runtimeStatusMapper = mock(LocalAgentRuntimeStatusMapper.class);
        scheduleMapper = mock(SelfMediaPublishScheduleMapper.class);
        brandAccessService = mock(BrandAccessService.class);
        service = new LocalAgentExecutionAuthorizationService(
                sessionMapper, runtimeStatusMapper, scheduleMapper, brandAccessService);
        now = LocalDateTime.of(2026, 7, 18, 16, 0);
    }

    @Test
    void accountWideSessionCanExecuteAuthorizedBoundBrand() {
        stubSession(null, "active", now.plusDays(1));
        stubRuntime(OPERATOR_ID);
        when(brandAccessService.hasBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.OPERATE))
                .thenReturn(true);
        when(scheduleMapper.isBrowserEnvironmentOwnedByLocalAgent(
                ENVIRONMENT_ID, SESSION_ID, BRAND_ID, OPERATOR_ID, now)).thenReturn(true);

        var result = service.evaluate(OPERATOR_ID, SESSION_ID, BRAND_ID, ENVIRONMENT_ID, now);

        assertTrue(result.authorized());
        assertEquals(null, result.reason());
    }

    @Test
    void legacyBrandSessionCannotCrossBrand() {
        stubSession(99L, "active", now.plusDays(1));

        var result = service.evaluate(OPERATOR_ID, SESSION_ID, BRAND_ID, ENVIRONMENT_ID, now);

        assertEquals("LOCAL_AGENT_SESSION_BRAND_MISMATCH", result.reason());
        verify(brandAccessService, never()).hasBrandAccess(any(), any(), any());
    }

    @Test
    void realtimeBrandPermissionIsRequired() {
        stubSession(null, "active", now.plusDays(1));
        when(brandAccessService.hasBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.OPERATE))
                .thenReturn(false);

        var result = service.evaluate(OPERATOR_ID, SESSION_ID, BRAND_ID, ENVIRONMENT_ID, now);

        assertEquals("NO_AUTHORIZED_BRAND", result.reason());
        verify(runtimeStatusMapper, never()).selectLatestBySessionId(any());
    }

    @Test
    void expiredSessionIsRejected() {
        stubSession(null, "active", now);

        var result = service.evaluate(OPERATOR_ID, SESSION_ID, BRAND_ID, ENVIRONMENT_ID, now);

        assertEquals("LOCAL_AGENT_SESSION_EXPIRED", result.reason());
    }

    @Test
    void missingCurrentRuntimeIsReportedOffline() {
        stubSession(null, "active", now.plusDays(1));
        when(brandAccessService.hasBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.OPERATE))
                .thenReturn(true);

        var result = service.evaluate(OPERATOR_ID, SESSION_ID, BRAND_ID, ENVIRONMENT_ID, now);

        assertEquals("HELPER_OFFLINE", result.reason());
    }

    @Test
    void environmentOutsideCurrentMachineProfileIsRejected() {
        stubSession(null, "active", now.plusDays(1));
        stubRuntime(OPERATOR_ID);
        when(brandAccessService.hasBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.OPERATE))
                .thenReturn(true);
        when(scheduleMapper.isBrowserEnvironmentOwnedByLocalAgent(
                ENVIRONMENT_ID, SESSION_ID, BRAND_ID, OPERATOR_ID, now)).thenReturn(false);

        var result = service.evaluate(OPERATOR_ID, SESSION_ID, BRAND_ID, ENVIRONMENT_ID, now);

        assertEquals("ENVIRONMENT_NOT_BOUND_TO_THIS_HELPER", result.reason());
    }

    @Test
    void callbackAuthorizationFailureThrowsWithoutMutationAtCallerBoundary() {
        stubSession(null, "active", now.plusDays(1));
        stubRuntime(OPERATOR_ID);
        when(brandAccessService.hasBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.OPERATE))
                .thenReturn(true);

        assertThrows(BizException.class,
                () -> service.requireAuthorized(OPERATOR_ID, SESSION_ID, BRAND_ID, ENVIRONMENT_ID, now));
    }

    private void stubSession(Long brandId, String status, LocalDateTime expiresAt) {
        LocalAgentSession session = new LocalAgentSession();
        session.setId(SESSION_ID);
        session.setOperatorId(OPERATOR_ID);
        session.setBrandId(brandId);
        session.setStatus(status);
        session.setExpiresAt(expiresAt);
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);
    }

    private void stubRuntime(Long operatorId) {
        LocalAgentRuntimeStatus runtime = new LocalAgentRuntimeStatus();
        runtime.setSessionId(SESSION_ID);
        runtime.setOperatorId(operatorId);
        when(runtimeStatusMapper.selectLatestBySessionId(SESSION_ID)).thenReturn(runtime);
    }
}
