package com.huanjing.geo.module.audit;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.credential.CredentialErrorCodes;
import com.huanjing.geo.module.customer.access.BrandAccessErrorCodes;
import com.huanjing.geo.module.extension.ExtensionErrorCodes;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditOperationAspectTest {

    private AuditService auditService;
    private AuditOperationAspect aspect;
    private AuditOperation operation;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() throws Exception {
        auditService = mock(AuditService.class);
        aspect = new AuditOperationAspect(auditService);
        operation = TestAuditedMethod.class.getDeclaredMethod("audited").getAnnotation(AuditOperation.class);
        joinPoint = mockJoinPoint();
    }

    @Test
    void successRecordsSuccess() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, operation);

        assertEquals("ok", result);
        assertEquals(AuditResult.SUCCESS, capturedEvent().getResult());
    }

    @Test
    void legacyForbiddenBizExceptionRecordsDeniedAndRethrows() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new BizException(403, "denied"));

        assertThrows(BizException.class, () -> aspect.around(joinPoint, operation));

        assertEquals(AuditResult.DENIED, capturedEvent().getResult());
    }

    @Test
    void credentialIntegrityViolationRecordsDeniedAndRethrows() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new BizException(
                CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION,
                "credential brand mismatch"
        ));

        assertThrows(BizException.class, () -> aspect.around(joinPoint, operation));

        AuditEvent event = capturedEvent();
        assertEquals(AuditResult.DENIED, event.getResult());
        assertEquals(String.valueOf(CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION), event.getErrorCode());
    }

    @Test
    void brandAccessDeniedRecordsDeniedAndRethrows() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new BizException(
                BrandAccessErrorCodes.BRAND_ACCESS_DENIED,
                "No permission to access this brand"
        ));

        assertThrows(BizException.class, () -> aspect.around(joinPoint, operation));

        AuditEvent event = capturedEvent();
        assertEquals(AuditResult.DENIED, event.getResult());
        assertEquals(String.valueOf(BrandAccessErrorCodes.BRAND_ACCESS_DENIED), event.getErrorCode());
    }

    @Test
    void extensionTokenDeniedRecordsDeniedAndRethrows() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new BizException(
                ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED,
                "fill token used or expired"
        ));

        assertThrows(BizException.class, () -> aspect.around(joinPoint, operation));

        AuditEvent event = capturedEvent();
        assertEquals(AuditResult.DENIED, event.getResult());
        assertEquals(String.valueOf(ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED), event.getErrorCode());
    }

    @Test
    void brandAccessNotFoundRecordsNotFoundAndRethrows() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new BizException(
                BrandAccessErrorCodes.BRAND_ACCESS_NOT_FOUND,
                "Brand not found"
        ));

        assertThrows(BizException.class, () -> aspect.around(joinPoint, operation));

        AuditEvent event = capturedEvent();
        assertEquals(AuditResult.NOT_FOUND, event.getResult());
        assertEquals(String.valueOf(BrandAccessErrorCodes.BRAND_ACCESS_NOT_FOUND), event.getErrorCode());
    }

    @Test
    void runtimeExceptionRecordsFailureAndRethrows() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> aspect.around(joinPoint, operation));

        assertEquals(AuditResult.FAILURE, capturedEvent().getResult());
    }

    private AuditEvent capturedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        return captor.getValue();
    }

    private ProceedingJoinPoint mockJoinPoint() throws NoSuchMethodException {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        Method method = TestAuditedMethod.class.getDeclaredMethod("audited");
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringTypeName()).thenReturn(TestAuditedMethod.class.getName());
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[0]);
        return point;
    }

    static class TestAuditedMethod {
        @AuditOperation(value = "TEST_AUDIT", mode = AuditMode.SYNC, sensitive = true)
        void audited() {
        }
    }
}
