package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.content.credential.CredentialErrorCodes;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialPlaintext;
import com.huanjing.geo.module.content.credential.service.CredentialVaultService;
import com.huanjing.geo.module.extension.ExtensionErrorCodes;
import com.huanjing.geo.module.extension.dto.ExtensionFillTokenConsumeResponse;
import com.huanjing.geo.module.extension.dto.FillTokenConsumeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtensionCredentialServiceTest {

    private FillTokenService fillTokenService;
    private CredentialVaultService credentialVaultService;
    private ExtensionTaskStateService taskStateService;
    private ExtensionAuditSupport auditSupport;
    private ExtensionCredentialService service;

    @BeforeEach
    void setUp() {
        fillTokenService = mock(FillTokenService.class);
        credentialVaultService = mock(CredentialVaultService.class);
        taskStateService = mock(ExtensionTaskStateService.class);
        auditSupport = mock(ExtensionAuditSupport.class);
        service = new ExtensionCredentialService(fillTokenService, credentialVaultService, taskStateService, auditSupport);
    }

    @Test
    void consumeFillTokenDecryptsCookiesWithSignedBrandContext() {
        FillTokenConsumeResponse consumed = new FillTokenConsumeResponse(
                20L,
                10L,
                99L,
                30L,
                200L,
                "nonce-1"
        );
        when(fillTokenService.consume("fill-token", 99L, 7L)).thenReturn(consumed);
        when(credentialVaultService.decryptActiveCookies(20L, 10L, 99L))
                .thenReturn(new CookieCredentialPlaintext(
                        20L,
                        10L,
                        "toutiao",
                        3,
                        "{\"cookies\":[]}",
                        "ua",
                        "{\"sessionid\":\"present\"}"
                ));

        ExtensionFillTokenConsumeResponse response = service.consumeFillTokenAndDecrypt("fill-token", 99L, 7L, "127.0.0.1");

        assertEquals(30L, response.taskTargetId());
        assertEquals("toutiao", response.platform());
        assertEquals(3, response.credentialVersion());
        assertEquals("{\"cookies\":[]}", response.cookiesJson());
        verify(taskStateService).markFillingFromFillTokenConsume(30L, 99L, 7L);
        verify(credentialVaultService).decryptActiveCookies(20L, 10L, 99L);
        verify(auditSupport).record(
                eq("COOKIE_DECRYPT_VIA_FILL_TOKEN"),
                eq(AuditResult.SUCCESS),
                eq(AuditMode.SYNC),
                eq(true),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(7L),
                eq("FILL_TOKEN"),
                eq("nonce-1"),
                eq(null),
                eq(null),
                any()
        );
    }

    @Test
    void credentialIntegrityFailureWritesDeniedAuditAndRethrows() {
        FillTokenConsumeResponse consumed = new FillTokenConsumeResponse(20L, 10L, 99L, 30L, 200L, "nonce-1");
        when(fillTokenService.consume("fill-token", 99L, 7L)).thenReturn(consumed);
        when(credentialVaultService.decryptActiveCookies(20L, 10L, 99L))
                .thenThrow(new BizException(CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION, "brand mismatch"));

        BizException ex = assertThrows(BizException.class,
                () -> service.consumeFillTokenAndDecrypt("fill-token", 99L, 7L, "127.0.0.1"));

        assertEquals(CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION, ex.getCode());
        verify(auditSupport).record(
                eq("COOKIE_DECRYPT_VIA_FILL_TOKEN"),
                eq(AuditResult.DENIED),
                eq(AuditMode.SYNC),
                eq(true),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(7L),
                eq("FILL_TOKEN"),
                eq("nonce-1"),
                eq(String.valueOf(CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION)),
                eq("brand mismatch"),
                any()
        );
    }

    @Test
    void credentialNotFoundWritesNotFoundAuditAndRethrows() {
        FillTokenConsumeResponse consumed = new FillTokenConsumeResponse(20L, 10L, 99L, 30L, 200L, "nonce-1");
        when(fillTokenService.consume("fill-token", 99L, 7L)).thenReturn(consumed);
        when(credentialVaultService.decryptActiveCookies(20L, 10L, 99L))
                .thenThrow(new BizException(CredentialErrorCodes.CREDENTIAL_NOT_FOUND, "not found"));

        BizException ex = assertThrows(BizException.class,
                () -> service.consumeFillTokenAndDecrypt("fill-token", 99L, 7L, "127.0.0.1"));

        assertEquals(CredentialErrorCodes.CREDENTIAL_NOT_FOUND, ex.getCode());
        verify(auditSupport).record(
                eq("COOKIE_DECRYPT_VIA_FILL_TOKEN"),
                eq(AuditResult.NOT_FOUND),
                eq(AuditMode.SYNC),
                eq(true),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(7L),
                eq("FILL_TOKEN"),
                eq("nonce-1"),
                eq(String.valueOf(CredentialErrorCodes.CREDENTIAL_NOT_FOUND)),
                eq("not found"),
                any()
        );
    }

    @Test
    void fillTokenConsumeFailureDoesNotWriteCookieDecryptAudit() {
        when(fillTokenService.consume("fill-token", 99L, 7L))
                .thenThrow(new BizException(ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED, "used"));

        BizException ex = assertThrows(BizException.class,
                () -> service.consumeFillTokenAndDecrypt("fill-token", 99L, 7L, "127.0.0.1"));

        assertEquals(ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED, ex.getCode());
        verify(taskStateService, never()).markFillingFromFillTokenConsume(any(), any(), any());
        verify(auditSupport, never()).record(
                eq("COOKIE_DECRYPT_VIA_FILL_TOKEN"),
                any(),
                any(),
                eq(true),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void credentialDecryptFailureDoesNotMarkTaskFilling() {
        FillTokenConsumeResponse consumed = new FillTokenConsumeResponse(20L, 10L, 99L, 30L, 200L, "nonce-1");
        when(fillTokenService.consume("fill-token", 99L, 7L)).thenReturn(consumed);
        when(credentialVaultService.decryptActiveCookies(20L, 10L, 99L))
                .thenThrow(new BizException(CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION, "brand mismatch"));

        BizException ex = assertThrows(BizException.class,
                () -> service.consumeFillTokenAndDecrypt("fill-token", 99L, 7L, "127.0.0.1"));

        assertEquals(CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION, ex.getCode());
        verify(taskStateService, never()).markFillingFromFillTokenConsume(any(), any(), any());
    }
}
