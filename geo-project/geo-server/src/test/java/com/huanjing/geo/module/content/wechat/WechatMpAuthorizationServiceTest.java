package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatMpAuthorizationServiceTest {

    private final WechatOpenPlatformProperties properties = new WechatOpenPlatformProperties();
    private final WechatComponentAccessTokenService componentAccessTokenService = mock(WechatComponentAccessTokenService.class);
    private final WechatOpenPlatformClient openPlatformClient = mock(WechatOpenPlatformClient.class);
    private final SelfMediaAccountMapper accountMapper = mock(SelfMediaAccountMapper.class);
    private final MpCredentialCipherService cipherService = mock(MpCredentialCipherService.class);
    private final WechatFuncInfoValidator funcInfoValidator = mock(WechatFuncInfoValidator.class);
    private final WechatAuthorizerTokenService authorizerTokenService = mock(WechatAuthorizerTokenService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private WechatMpAuthorizationService service;

    @BeforeEach
    void setUp() {
        properties.setComponentAppid("component-appid");
        properties.setBackendAuthCallbackUrl("https://backend.example/api/wechat/open-platform/auth/callback");
        when(componentAccessTokenService.getAccessToken()).thenReturn("component-token");
        when(openPlatformClient.createPreAuthCode("component-token", "component-appid"))
                .thenReturn(new WechatOpenPlatformClient.PreAuthCodeResult("pre-auth-code", 600));
        when(cipherService.encryptForStorage("refresh-token")).thenReturn("refresh-cipher");
        when(cipherService.encryptForStorage("refresh-token-2")).thenReturn("refresh-cipher-2");
        when(funcInfoValidator.hasDraftPermissions(any())).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new WechatMpAuthorizationService(
                properties,
                componentAccessTokenService,
                openPlatformClient,
                accountMapper,
                cipherService,
                funcInfoValidator,
                authorizerTokenService,
                redisTemplate,
                objectMapper
        );
    }

    @Test
    void buildAuthUrlPutsStateInsideRedirectUri() {
        var authUrl = service.buildAuthUrl(5L, 99L);

        assertThat(authUrl.getAuthUrl()).contains("component_appid=component-appid");
        assertThat(authUrl.getAuthUrl()).contains("pre_auth_code=pre-auth-code");
        assertThat(authUrl.getAuthUrl()).contains("redirect_uri=https://backend.example/api/wechat/open-platform/auth/callback?state%3D");
        assertThat(authUrl.getAuthUrl()).doesNotContain("&state=");
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), anyString(), any(Duration.class));
        assertThat(keyCaptor.getValue()).startsWith("wechat:auth_state:");
    }

    @Test
    void saveOrUpdateAuthorizationInsertsNewAuthorizer() {
        queryAuth("auth-code", "refresh-token");
        authorizerInfo("[]");

        SelfMediaAccount account = service.saveOrUpdateAuthorization("component-appid", "auth-code");

        assertThat(account.getPlatform()).isEqualTo("wechat_mp");
        assertThat(account.getPlatformAccountId()).isEqualTo("wx-authorizer");
        assertThat(account.getRefreshTokenCipher()).isEqualTo("refresh-cipher");
        assertThat(account.getStatus()).isEqualTo("active");
        verify(accountMapper).insert(account);
        verify(authorizerTokenService).evictAccessToken(account);
    }

    @Test
    void saveOrUpdateAuthorizationUpdatesExistingRefreshToken() {
        SelfMediaAccount existing = new SelfMediaAccount();
        existing.setId(99L);
        existing.setBrandId(7L);
        existing.setPlatform("wechat_mp");
        existing.setPlatformAccountId("wx-authorizer");
        existing.setRefreshTokenCipher("old-refresh-cipher");
        when(accountMapper.selectByPlatformAccountIncludingDeleted("wechat_mp", "wx-authorizer"))
                .thenReturn(existing);
        queryAuth("new-auth-code", "refresh-token-2");
        authorizerInfo("[]");

        SelfMediaAccount account = service.saveOrUpdateAuthorization("component-appid", "new-auth-code");

        assertThat(account.getId()).isEqualTo(99L);
        assertThat(account.getBrandId()).isEqualTo(7L);
        assertThat(account.getRefreshTokenCipher()).isEqualTo("refresh-cipher-2");
        verify(accountMapper).updateById(account);
        verify(authorizerTokenService).evictAccessToken(account);
    }

    @Test
    void saveOrUpdateAuthorizationRestoresDeletedAuthorizerForBrand() {
        SelfMediaAccount existing = new SelfMediaAccount();
        existing.setId(99L);
        existing.setBrandId(7L);
        existing.setPlatform("wechat_mp");
        existing.setPlatformAccountId("wx-authorizer");
        existing.setRefreshTokenCipher("old-refresh-cipher");
        existing.setDeletedAt(LocalDateTime.now().minusDays(1));
        existing.setDeletedBy(100L);
        when(accountMapper.selectByPlatformAccountIncludingDeleted("wechat_mp", "wx-authorizer"))
                .thenReturn(existing);
        queryAuth("new-auth-code", "refresh-token-2");
        authorizerInfo("[]");

        SelfMediaAccount account = service.saveOrUpdateAuthorization(5L, "component-appid", "new-auth-code");

        assertThat(account.getId()).isEqualTo(99L);
        assertThat(account.getBrandId()).isEqualTo(5L);
        assertThat(account.getRefreshTokenCipher()).isEqualTo("refresh-cipher-2");
        assertThat(account.getStatus()).isEqualTo("active");
        verify(accountMapper).restoreWechatAuthorization(eq(account), any(LocalDateTime.class));
        verify(authorizerTokenService).evictAccessToken(account);
    }

    private void queryAuth(String authCode, String refreshToken) {
        when(openPlatformClient.queryAuth("component-token", "component-appid", authCode))
                .thenReturn(new WechatOpenPlatformClient.QueryAuthResult(
                        "wx-authorizer",
                        "access-token",
                        refreshToken,
                        7200,
                        "[]"
                ));
    }

    private void authorizerInfo(String funcInfoJson) {
        when(openPlatformClient.getAuthorizerInfo("component-token", "component-appid", "wx-authorizer"))
                .thenReturn(new WechatOpenPlatformClient.AuthorizerInfoResult(
                        "公众号",
                        "https://img.example/avatar.png",
                        "https://img.example/qrcode.png",
                        "主体",
                        "0",
                        funcInfoJson
                ));
    }
}
