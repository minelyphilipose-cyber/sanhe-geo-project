package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.config.WechatMpClientProperties;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialMeta;
import com.huanjing.geo.module.content.credential.service.CredentialVaultService;
import com.huanjing.geo.module.content.dto.SelfMediaAccountManageRequest;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.vo.WechatMpCapabilityVO;
import com.huanjing.geo.module.content.wechat.WechatAuthorizerTokenService;
import com.huanjing.geo.module.content.wechat.WechatComponentTicketService;
import com.huanjing.geo.module.content.wechat.WechatMpClient;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaAccountServiceTest {

    private SelfMediaAccountMapper selfMediaAccountMapper;
    private CredentialVaultService credentialVaultService;
    private BrandAccessService brandAccessService;
    private CurrentUserService currentUserService;
    private SelfMediaAccountService service;

    @BeforeEach
    void setUp() {
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        credentialVaultService = mock(CredentialVaultService.class);
        brandAccessService = mock(BrandAccessService.class);
        currentUserService = mock(CurrentUserService.class);

        service = new SelfMediaAccountService(
                selfMediaAccountMapper,
                mock(WechatMpClient.class),
                mock(WechatOpenPlatformProperties.class),
                mock(WechatMpClientProperties.class),
                mock(MpCredentialCipherService.class),
                mock(WechatAuthorizerTokenService.class),
                mock(WechatComponentTicketService.class),
                credentialVaultService,
                brandAccessService,
                currentUserService
        );

        SysUser operator = new SysUser();
        operator.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(selfMediaAccountMapper.selectOne(any())).thenReturn(null);
    }

    @Test
    void createCookieAccountGeneratesPlatformAccountId() {
        SelfMediaAccountManageRequest request = new SelfMediaAccountManageRequest(
                "toutiao",
                "头条运营号",
                null,
                "active"
        );

        service.createCookieAccount(10L, request);

        ArgumentCaptor<SelfMediaAccount> captor = ArgumentCaptor.forClass(SelfMediaAccount.class);
        verify(selfMediaAccountMapper).insert(captor.capture());
        SelfMediaAccount saved = captor.getValue();
        assertEquals("toutiao", saved.getPlatform());
        assertEquals("头条运营号", saved.getAccountName());
        assertTrue(saved.getPlatformAccountId().startsWith("geo-toutiao-10-"));
        assertEquals(31, saved.getPlatformAccountId().length());
        verify(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.MANAGE);
    }

    @Test
    void updateCookieAccountKeepsExistingPlatformAccountId() {
        SelfMediaAccount existing = new SelfMediaAccount();
        existing.setId(20L);
        existing.setBrandId(10L);
        existing.setPlatform("toutiao");
        existing.setPlatformAccountId("geo-toutiao-10-abc123456789abcd");
        existing.setAccountName("旧账号");
        existing.setAuthMode("COOKIE");
        existing.setStatus("active");
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(existing);

        SelfMediaAccountManageRequest request = new SelfMediaAccountManageRequest(
                "toutiao",
                "新账号",
                null,
                "disabled"
        );

        service.updateCookieAccount(20L, request);

        ArgumentCaptor<SelfMediaAccount> captor = ArgumentCaptor.forClass(SelfMediaAccount.class);
        verify(selfMediaAccountMapper).updateById(captor.capture());
        SelfMediaAccount saved = captor.getValue();
        assertEquals("geo-toutiao-10-abc123456789abcd", saved.getPlatformAccountId());
        assertEquals("新账号", saved.getAccountName());
        assertEquals("disabled", saved.getStatus());
    }

    @Test
    void capabilityMarksLiveVerificationBlockedWhileDraftCanBeTested() {
        WechatOpenPlatformProperties openProperties = new WechatOpenPlatformProperties();
        openProperties.setDraftDistributionEnabled(true);
        openProperties.setAutoPublishEnabled(false);
        openProperties.setLiveVerificationBlocked(true);
        openProperties.setLiveVerificationReason("domain_icp_filing_pending");
        openProperties.setComponentAppid("component-appid");
        openProperties.setComponentAppSecret("secret");
        openProperties.setToken("token");
        openProperties.setEncodingAesKey("abcdefghijklmnopqrstuvwxyzABCDEFGH123456789");
        openProperties.setBackendAuthCallbackUrl("https://www.example.com/api/wechat/open-platform/auth/callback");
        openProperties.setFrontendCallbackUrl("https://www.example.com/admin/content/execution");
        openProperties.setComponentEventUrl("https://www.example.com/api/wechat/open-platform/events");
        openProperties.setAuthorizerMessageUrl("https://www.example.com/api/wechat/open-platform/messages/$APPID");
        WechatMpClientProperties clientProperties = new WechatMpClientProperties();
        clientProperties.setMode("mock");
        WechatComponentTicketService ticketService = mock(WechatComponentTicketService.class);
        when(ticketService.getLatestReceivedAt("component-appid")).thenReturn(LocalDateTime.of(2026, 6, 9, 10, 0));
        SelfMediaAccountService capabilityService = new SelfMediaAccountService(
                selfMediaAccountMapper,
                mock(WechatMpClient.class),
                openProperties,
                clientProperties,
                mock(MpCredentialCipherService.class),
                mock(WechatAuthorizerTokenService.class),
                ticketService,
                mock(CredentialVaultService.class),
                brandAccessService,
                currentUserService
        );

        WechatMpCapabilityVO capability = capabilityService.capability();

        assertTrue(capability.isDraftDistributionEnabled());
        assertFalse(capability.isAutoPublishEnabled());
        assertTrue(capability.isLiveVerificationBlocked());
        assertEquals("domain_icp_filing_pending", capability.getLiveVerificationReason());
        assertTrue(capability.getDescription().contains("草稿箱接口可继续用于测试"));
        assertTrue(capability.getReadinessChecks().stream().anyMatch(item ->
                "component_verify_ticket".equals(item.getCode()) && "ok".equals(item.getStatus())));
    }

    @Test
    void destroyCookieCredentialClearsVaultAndKeepsAccountManageable() {
        SelfMediaAccount existing = new SelfMediaAccount();
        existing.setId(20L);
        existing.setBrandId(10L);
        existing.setPlatform("toutiao");
        existing.setPlatformAccountId("geo-toutiao-10-abc123456789abcd");
        existing.setAccountName("头条账号");
        existing.setAuthMode("COOKIE");
        existing.setStatus("active");
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(existing);

        service.destroyCookieCredential(20L);

        verify(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.MANAGE);
        verify(credentialVaultService).destroyCredentials(20L, 10L, 99L);
        ArgumentCaptor<SelfMediaAccount> captor = ArgumentCaptor.forClass(SelfMediaAccount.class);
        verify(selfMediaAccountMapper).updateById(captor.capture());
        assertEquals("active", captor.getValue().getStatus());
        assertEquals("cookie credential cleared by operator", captor.getValue().getLastAuthError());
    }

    @Test
    void listByBrandIncludesCookieCredentialIdentityStatus() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(20L);
        account.setBrandId(10L);
        account.setPlatform("toutiao");
        account.setPlatformAccountId("geo-toutiao-10-abc123456789abcd");
        account.setAccountName("头条账号");
        account.setAuthMode("COOKIE");
        account.setStatus("active");
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of(account));
        LocalDateTime capturedAt = LocalDateTime.of(2026, 5, 22, 10, 0);
        when(credentialVaultService.getActiveCredentialMeta(20L)).thenReturn(new CookieCredentialMeta(
                88L,
                20L,
                10L,
                "toutiao",
                2,
                null,
                null,
                null,
                null,
                "Mozilla/5.0",
                "{\"platformIdentity\":{\"displayName\":\"头条账号\"},\"identityCheck\":{\"status\":\"matched\",\"message\":\"当前平台账号匹配\"}}",
                "{\"sessionid\":\"present\"}",
                99L,
                capturedAt,
                capturedAt,
                null,
                null,
                capturedAt
        ));

        var accounts = service.listByBrand(10L);

        assertEquals("active", accounts.get(0).getCookieCredentialStatus());
        assertEquals("matched", accounts.get(0).getCookieCredentialIdentityStatus());
        assertEquals("头条账号", accounts.get(0).getCookieCredentialIdentityName());
    }
}
