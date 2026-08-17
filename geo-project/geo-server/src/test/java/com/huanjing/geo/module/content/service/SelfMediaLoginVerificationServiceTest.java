package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.BrowserEnvironmentConstants;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentLoginStatusRequest;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAuthHealthPolicy;
import com.huanjing.geo.module.content.entity.SelfMediaLoginVerification;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaLoginVerificationMapper;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelfMediaLoginVerificationServiceTest {
    private SelfMediaLoginVerificationMapper verificationMapper;
    private SelfMediaAccountMapper accountMapper;
    private SelfMediaAuthHealthPolicyService policyService;
    private SystemAlertService alertService;
    private ApplicationEventPublisher eventPublisher;
    private SelfMediaLoginVerificationService service;

    @BeforeEach
    void setUp() {
        verificationMapper = mock(SelfMediaLoginVerificationMapper.class);
        accountMapper = mock(SelfMediaAccountMapper.class);
        policyService = mock(SelfMediaAuthHealthPolicyService.class);
        alertService = mock(SystemAlertService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new SelfMediaLoginVerificationService(
                verificationMapper,
                accountMapper,
                mock(BrowserEnvironmentAccountMapper.class),
                mock(BrowserEnvironmentMapper.class),
                mock(BrandAccessService.class),
                mock(CurrentUserService.class),
                policyService,
                alertService,
                eventPublisher
        );
    }

    @Test
    void matchingNameWithDifferentIdSucceedsWithoutWarning() {
        SelfMediaLoginVerification verification = verification("三和口腔", "old-id");
        SelfMediaAccount account = account();
        BrowserEnvironmentAccount binding = binding();
        when(verificationMapper.selectById(1L)).thenReturn(verification);
        SelfMediaAuthHealthPolicy policy = new SelfMediaAuthHealthPolicy();
        policy.setEnabled(true);
        policy.setReverifyIntervalDays(14);
        when(policyService.findPolicy("toutiao")).thenReturn(policy);

        service.completeFromLoginReport(1L, binding, account,
                new BrowserEnvironmentLoginStatusRequest("env-1", 10L, "toutiao", "new-id",
                        " 三和口腔 ", BrowserEnvironmentConstants.LOGIN_LOGGED_IN, null, null, 1L),
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN);

        assertEquals("succeeded", verification.getStatus());
        assertEquals("SUCCESS", verification.getResultCode());
        assertEquals("登录状态验证成功", verification.getResultMessage());
        assertEquals("new-id", verification.getActualPlatformAccountId());
        assertEquals("success", account.getLastLoginVerificationResult());
        assertNull(account.getLastLoginVerificationWarning());
        assertNotNull(account.getLastLoginVerifiedAt());
        assertEquals(account.getLastLoginVerifiedAt(), account.getLastLoginVerificationCheckedAt());
        assertNotNull(account.getRecommendedReverifyAt());
        verify(alertService).resolveOpenByDedupeKeyPrefix("self_media_auth:10:", null);
    }

    @Test
    void identityMismatchIsInconclusiveAndPreservesLastTrustedLoginFact() {
        SelfMediaLoginVerification verification = verification("三和口腔", null);
        SelfMediaAccount account = account();
        LocalDateTime lastSuccess = LocalDateTime.now().minusDays(2);
        account.setLastLoginVerifiedAt(lastSuccess);
        account.setLastLoginVerificationResult("success");
        when(verificationMapper.selectById(1L)).thenReturn(verification);

        service.completeFromLoginReport(1L, binding(), account,
                new BrowserEnvironmentLoginStatusRequest("env-1", 10L, "toutiao", null,
                        "其他账号", BrowserEnvironmentConstants.LOGIN_LOGGED_IN, null, null, 1L),
                BrowserEnvironmentConstants.LOGIN_MISMATCH);

        assertEquals("inconclusive", verification.getStatus());
        assertEquals("ACCOUNT_NAME_MISMATCH", verification.getResultCode());
        assertEquals(lastSuccess, account.getLastLoginVerifiedAt());
        assertEquals("success", account.getLastLoginVerificationResult());
        assertEquals(SelfMediaLoginVerificationService.METHOD_SYSTEM_TRIGGERED,
                account.getLastLoginVerificationMethod());
        assertNotNull(account.getLastLoginVerificationCheckedAt());
        assertTrue(account.getLastLoginVerificationWarning().contains("其他账号"));
        verify(accountMapper).updateById(account);
    }

    @Test
    void trustedPassiveReportCreatesHealthFactWithoutCookieCredential() {
        SelfMediaAccount account = account();
        account.setPlatformAccountId("random-id-saved-by-system");
        account.setLastLoginVerificationWarning("扩展读取到多个账号身份");
        SelfMediaAuthHealthPolicy policy = new SelfMediaAuthHealthPolicy();
        policy.setEnabled(true);
        policy.setReverifyIntervalDays(14);
        when(policyService.findPolicy("toutiao")).thenReturn(policy);

        boolean recorded = service.recordTrustedPassiveHealthReport(binding(), account,
                new BrowserEnvironmentLoginStatusRequest("env-1", 10L, "toutiao", "actual-platform-id",
                        " 三和口腔 ", BrowserEnvironmentConstants.LOGIN_LOGGED_IN, null, null));

        assertTrue(recorded);
        assertEquals(SelfMediaLoginVerificationService.METHOD_EXTENSION_PASSIVE,
                account.getLastLoginVerificationMethod());
        assertNull(account.getLastLoginVerificationWarning());
        assertNotNull(account.getLastLoginVerifiedAt());
        assertNotNull(account.getRecommendedReverifyAt());
        verify(accountMapper).updateById(account);
        verify(accountMapper).updateNullableLoginHealthFields(
                eq(10L), isNull(), eq(account.getRecommendedReverifyAt()));
        verify(alertService).resolveOpenByDedupeKeyPrefix("self_media_auth:10:", null);
    }

    @Test
    void passiveReportWithNonDeterministicNameMatchIsNotHealthFact() {
        SelfMediaAccount account = account();

        boolean recorded = service.recordTrustedPassiveHealthReport(binding(), account,
                new BrowserEnvironmentLoginStatusRequest("env-1", 10L, "toutiao", null,
                        "头条/三和口腔", BrowserEnvironmentConstants.LOGIN_LOGGED_IN, null, null));

        assertFalse(recorded);
        assertNull(account.getLastLoginVerifiedAt());
        assertNull(account.getLastLoginVerificationMethod());
        assertNull(account.getLastLoginVerificationWarning());
        verify(accountMapper, never()).updateById(any());
    }

    @Test
    void unreadablePassiveIdentityDoesNotCreateFailedHealthFact() {
        SelfMediaAccount account = account();

        boolean recorded = service.recordTrustedPassiveHealthReport(binding(), account,
                new BrowserEnvironmentLoginStatusRequest("env-1", 10L, "toutiao", null,
                        null, "error", "IDENTITY_UNREADABLE", "selector miss"));

        assertFalse(recorded);
        assertNull(account.getLastLoginVerificationResult());
        assertNull(account.getLastLoginVerificationCheckedAt());
        assertNull(account.getLastLoginVerificationWarning());
        verify(accountMapper, never()).updateById(any());
    }

    @Test
    void passiveLoginRequiredPreservesExistingSuccessfulHealthFact() {
        SelfMediaAccount account = account();
        LocalDateTime lastSuccess = LocalDateTime.now().minusDays(3);
        account.setLastLoginVerifiedAt(lastSuccess);
        account.setLastLoginVerificationResult("success");
        account.setRecommendedReverifyAt(LocalDateTime.now().plusDays(11));

        boolean recorded = service.recordTrustedPassiveHealthReport(binding(), account,
                new BrowserEnvironmentLoginStatusRequest("env-1", 10L, "toutiao", null,
                        null, BrowserEnvironmentConstants.LOGIN_REQUIRED, null, null));

        assertFalse(recorded);
        assertEquals(lastSuccess, account.getLastLoginVerifiedAt());
        assertEquals("success", account.getLastLoginVerificationResult());
        assertNull(account.getLastLoginVerificationCheckedAt());
        assertNotNull(account.getRecommendedReverifyAt());
        assertNull(account.getLastLoginVerificationWarning());
        verify(accountMapper, never()).updateById(any());
        verify(accountMapper, never()).updateNullableLoginHealthFields(any(), any(), any());
    }

    @Test
    void invalidatingOneAccountDoesNotTouchAnyOtherProductionAccount() {
        service.invalidateAccountHealth(
                10L,
                SelfMediaLoginVerificationService.METHOD_BINDING_INVALIDATED,
                "指纹浏览器环境已解绑"
        );

        verify(accountMapper).invalidateLoginHealth(
                eq(10L), any(LocalDateTime.class),
                eq(SelfMediaLoginVerificationService.METHOD_BINDING_INVALIDATED),
                eq("指纹浏览器环境已解绑"));
        verify(accountMapper, never()).selectList(any());
        verify(alertService).resolveOpenByDedupeKeyPrefix("self_media_auth:10:", null);
        verify(eventPublisher).publishEvent(any(com.huanjing.geo.module.content.event.SelfMediaAuthHealthChangedEvent.class));
    }

    @Test
    void publishTaskLoginFailureBecomesLatestHealthDiagnostic() {
        SelfMediaAccount account = account();
        LocalDateTime previousSuccess = LocalDateTime.now().minusDays(2);
        account.setLastLoginVerifiedAt(previousSuccess);
        when(accountMapper.selectById(10L)).thenReturn(account);

        service.recordPublishTaskLoginFailure(10L, "平台发布页要求重新登录");

        assertEquals(previousSuccess, account.getLastLoginVerifiedAt());
        assertEquals("failed", account.getLastLoginVerificationResult());
        assertEquals(SelfMediaLoginVerificationService.METHOD_PUBLISH_TASK_FAILURE,
                account.getLastLoginVerificationMethod());
        assertEquals("平台发布页要求重新登录", account.getLastLoginVerificationWarning());
        assertNull(account.getRecommendedReverifyAt());
        verify(accountMapper).updateNullableLoginHealthFields(
                eq(10L), eq("平台发布页要求重新登录"), isNull());
        verify(eventPublisher).publishEvent(any(com.huanjing.geo.module.content.event.SelfMediaAuthHealthChangedEvent.class));
    }

    private SelfMediaLoginVerification verification(String name, String id) {
        SelfMediaLoginVerification row = new SelfMediaLoginVerification();
        row.setId(1L);
        row.setBrandId(2L);
        row.setSelfMediaAccountId(10L);
        row.setBrowserEnvironmentId(20L);
        row.setBrowserEnvironmentAccountId(30L);
        row.setPlatform("toutiao");
        row.setExpectedAccountName(name);
        row.setExpectedPlatformAccountId(id);
        row.setStatus("pending");
        row.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        return row;
    }

    private SelfMediaAccount account() {
        SelfMediaAccount row = new SelfMediaAccount();
        row.setId(10L);
        row.setBrandId(2L);
        row.setPlatform("toutiao");
        row.setAccountName("三和口腔");
        return row;
    }

    private BrowserEnvironmentAccount binding() {
        BrowserEnvironmentAccount row = new BrowserEnvironmentAccount();
        row.setId(30L);
        row.setBrandId(2L);
        row.setBrowserEnvironmentId(20L);
        row.setSelfMediaAccountId(10L);
        row.setPlatform("toutiao");
        return row;
    }
}
