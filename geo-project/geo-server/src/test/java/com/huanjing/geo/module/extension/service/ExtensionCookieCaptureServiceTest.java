package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialMeta;
import com.huanjing.geo.module.content.credential.service.CredentialVaultService;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessErrorCodes;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.dto.ExtensionCookieCaptureRequest;
import com.huanjing.geo.module.extension.dto.ExtensionCookieCaptureResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.COOKIE_CAPTURE_ACCOUNT_BRAND_MISMATCH;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.COOKIE_CAPTURE_CONFIRM_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtensionCookieCaptureServiceTest {

    private static final String COOKIE_JSON = "[{\"name\":\"sessionid\",\"value\":\"secret-cookie-value\"}]";

    private SelfMediaAccountMapper accountMapper;
    private BrandAccessService brandAccessService;
    private CredentialVaultService credentialVaultService;
    private ExtensionRedisStore redisStore;
    private ExtensionAuditSupport auditSupport;
    private ExtensionCookieCaptureService service;

    @BeforeEach
    void setUp() {
        accountMapper = mock(SelfMediaAccountMapper.class);
        brandAccessService = mock(BrandAccessService.class);
        credentialVaultService = mock(CredentialVaultService.class);
        redisStore = mock(ExtensionRedisStore.class);
        auditSupport = mock(ExtensionAuditSupport.class);
        service = new ExtensionCookieCaptureService(
                accountMapper,
                brandAccessService,
                credentialVaultService,
                redisStore,
                auditSupport
        );
    }

    @Test
    void captureStoresCookiesAndAuditsSensitiveSuccess() {
        when(accountMapper.selectById(20L)).thenReturn(account(20L, 10L, "toutiao"));
        when(redisStore.tryLock(eq("geo:extension:cookie-capture:nonce:99:nonce-1"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        LocalDateTime capturedAt = LocalDateTime.of(2026, 5, 7, 12, 0);
        when(credentialVaultService.storeCapturedCookies(any())).thenReturn(new CookieCredentialMeta(
                88L, 20L, 10L, "toutiao", 3, null, null, null, null,
                "Mozilla/5.0", "{\"browser\":\"chrome\"}", "{\"sessionid\":\"present\"}",
                99L, capturedAt, capturedAt, null, null, capturedAt
        ));

        ExtensionCookieCaptureResponse response = service.capture(request(true, 10L, 20L), 99L, 77L);

        assertEquals(88L, response.credentialId());
        assertEquals("ACTIVE", response.status());
        verify(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.MANAGE);
        verify(credentialVaultService).storeCapturedCookies(any());
        verify(auditSupport).record(
                eq("EXTENSION_COOKIE_CAPTURE"),
                eq(AuditResult.SUCCESS),
                eq(AuditMode.SYNC),
                eq(true),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(null),
                eq(77L),
                eq("SELF_MEDIA_ACCOUNT"),
                eq("20"),
                eq(null),
                eq(null),
                any()
        );
    }

    @Test
    void captureRejectsMissingOperatorConfirmationBeforeVaultCall() {
        BizException ex = assertThrows(BizException.class,
                () -> service.capture(request(false, 10L, 20L), 99L, 77L));

        assertEquals(COOKIE_CAPTURE_CONFIRM_REQUIRED, ex.getCode());
        assertFalse(ex.getMessage().contains("secret-cookie-value"));
        verify(credentialVaultService, never()).storeCapturedCookies(any());
    }

    @Test
    void captureRejectsCrossBrandAccountWithoutLeakingCookie() {
        when(accountMapper.selectById(20L)).thenReturn(account(20L, 11L, "toutiao"));

        BizException ex = assertThrows(BizException.class,
                () -> service.capture(request(true, 10L, 20L), 99L, 77L));

        assertEquals(COOKIE_CAPTURE_ACCOUNT_BRAND_MISMATCH, ex.getCode());
        assertFalse(ex.getMessage().contains("secret-cookie-value"));
        verify(credentialVaultService, never()).storeCapturedCookies(any());
    }

    @Test
    void capturePropagatesBrandAccessManageRejection() {
        doThrow(new BizException(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, "denied"))
                .when(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.MANAGE);

        BizException ex = assertThrows(BizException.class,
                () -> service.capture(request(true, 10L, 20L), 99L, 77L));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, ex.getCode());
        verify(accountMapper, never()).selectById(any());
        verify(credentialVaultService, never()).storeCapturedCookies(any());
    }

    @Test
    void duplicateConfirmNonceRejectsBeforeVaultCall() {
        when(accountMapper.selectById(20L)).thenReturn(account(20L, 10L, "toutiao"));
        when(redisStore.tryLock(any(), eq("1"), any(Duration.class))).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> service.capture(request(true, 10L, 20L), 99L, 77L));

        assertEquals(70016, ex.getCode());
        verify(credentialVaultService, never()).storeCapturedCookies(any());
    }

    private ExtensionCookieCaptureRequest request(boolean confirmed, Long brandId, Long accountId) {
        return new ExtensionCookieCaptureRequest(
                brandId,
                accountId,
                "toutiao",
                "0.1.0",
                "install-1",
                confirmed,
                "nonce-1",
                COOKIE_JSON,
                "Mozilla/5.0",
                "{\"sessionid\":\"present\"}",
                "{\"browser\":\"chrome\"}"
        );
    }

    private SelfMediaAccount account(Long accountId, Long brandId, String platform) {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(accountId);
        account.setBrandId(brandId);
        account.setPlatform(platform);
        account.setAccountName("Toutiao Account");
        return account;
    }
}
