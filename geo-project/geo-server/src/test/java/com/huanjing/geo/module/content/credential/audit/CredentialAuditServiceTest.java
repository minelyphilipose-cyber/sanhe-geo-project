package com.huanjing.geo.module.content.credential.audit;

import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CredentialAuditServiceTest {

    private AuditService auditService;
    private CredentialAuditService credentialAuditService;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        credentialAuditService = new CredentialAuditService(auditService);
    }

    @Test
    void decryptIsSensitiveSyncSuccess() {
        credentialAuditService.onCredentialDecrypted(meta(), 99L);

        AuditEvent event = capturedEvent();
        assertEquals("CREDENTIAL_DECRYPT", event.getEventType());
        assertEquals(AuditMode.SYNC, event.getMode());
        assertEquals(AuditResult.SUCCESS, event.getResult());
        assertTrue(event.isSensitive());
        assertEquals(99L, event.getActorId());
    }

    @Test
    void accessDeniedIsSensitiveSyncDenied() {
        credentialAuditService.onCredentialAccessDenied(20L, 11L, 10L, 99L, "BRAND_MISMATCH");

        AuditEvent event = capturedEvent();
        assertEquals("CREDENTIAL_DECRYPT", event.getEventType());
        assertEquals(AuditMode.SYNC, event.getMode());
        assertEquals(AuditResult.DENIED, event.getResult());
        assertTrue(event.isSensitive());
        assertEquals(10L, event.getBrandId());
    }

    @Test
    void destroyedZeroRowsIsNoOp() {
        credentialAuditService.onCredentialDestroyed(20L, 99L, 0);

        AuditEvent event = capturedEvent();
        assertEquals("CREDENTIAL_DESTROY", event.getEventType());
        assertEquals(AuditResult.NO_OP, event.getResult());
        assertEquals(AuditMode.SYNC, event.getMode());
    }

    private AuditEvent capturedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        return captor.getValue();
    }

    private CookieCredentialMeta meta() {
        return new CookieCredentialMeta(
                100L,
                20L,
                10L,
                "toutiao",
                1,
                "local-test",
                "AES-256-GCM",
                "iv",
                "brandId=10|accountId=20|platform=toutiao|version=1",
                "Mozilla/5.0",
                null,
                "{\"sessionid\":\"present\"}",
                99L,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                LocalDateTime.now()
        );
    }
}
