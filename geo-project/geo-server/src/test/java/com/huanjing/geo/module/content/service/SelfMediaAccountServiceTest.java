package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.config.WechatMpClientProperties;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.credential.service.CredentialVaultService;
import com.huanjing.geo.module.content.dto.SelfMediaAccountManageRequest;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.wechat.WechatAuthorizerTokenService;
import com.huanjing.geo.module.content.wechat.WechatMpClient;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaAccountServiceTest {

    private SelfMediaAccountMapper selfMediaAccountMapper;
    private BrandAccessService brandAccessService;
    private CurrentUserService currentUserService;
    private SelfMediaAccountService service;

    @BeforeEach
    void setUp() {
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        brandAccessService = mock(BrandAccessService.class);
        currentUserService = mock(CurrentUserService.class);

        service = new SelfMediaAccountService(
                selfMediaAccountMapper,
                mock(WechatMpClient.class),
                mock(WechatOpenPlatformProperties.class),
                mock(WechatMpClientProperties.class),
                mock(MpCredentialCipherService.class),
                mock(WechatAuthorizerTokenService.class),
                mock(CredentialVaultService.class),
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
}
