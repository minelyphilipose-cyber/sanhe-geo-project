package com.huanjing.geo.module.content.douyin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.DouyinClientProperties;
import com.huanjing.geo.module.content.config.DouyinFeatureProperties;
import com.huanjing.geo.module.content.config.DouyinOpenPlatformProperties;
import com.huanjing.geo.module.content.douyin.client.DouyinClient;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCodeTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinTokenResponse;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.vo.DouyinCapabilityVO;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinAuthorizationServiceTest {
    private DouyinOpenPlatformProperties properties;
    private DouyinFeatureProperties featureProperties;
    private DouyinClientProperties clientProperties;
    private DouyinClient douyinClient;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private MpCredentialCipherService cipherService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private DouyinAuthorizationService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SelfMediaAccount.class);
        properties = new DouyinOpenPlatformProperties();
        properties.setClientKey("client-key");
        properties.setClientSecret("client-secret");
        properties.setAuthPageUrl("https://open.douyin.com/platform/oauth/connect/");
        properties.setAuthCallbackUrl("http://localhost:8080/api/douyin/open-platform/auth/callback");
        properties.setFrontendCallbackUrl("http://localhost:5173/admin/content/execution");
        properties.setRequiredScopes(List.of("video.create.bind"));
        featureProperties = new DouyinFeatureProperties();
        clientProperties = new DouyinClientProperties();
        douyinClient = mock(DouyinClient.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        cipherService = mock(MpCredentialCipherService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new DouyinAuthorizationService(
                properties,
                featureProperties,
                clientProperties,
                douyinClient,
                selfMediaAccountMapper,
                cipherService,
                redisTemplate,
                objectMapper
        );
    }

    @Test
    void buildAuthUrl_writesStateAndContainsRequiredOAuthParams() {
        properties.setAuthPageUrl("https://douyin-auth.example/connect/");

        var authUrl = service.buildAuthUrl(10L, 20L);

        assertEquals(600, authUrl.getExpiresIn());
        assertTrue(authUrl.getAuthUrl().startsWith("https://douyin-auth.example/connect/"));
        assertTrue(authUrl.getAuthUrl().contains("client_key=client-key"));
        assertTrue(authUrl.getAuthUrl().contains("response_type=code"));
        assertTrue(authUrl.getAuthUrl().contains("scope=video.create.bind"));
        assertTrue(authUrl.getAuthUrl().contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fdouyin%2Fopen-platform%2Fauth%2Fcallback"));
        assertTrue(authUrl.getAuthUrl().contains("state="));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofMinutes(10)));
        assertTrue(keyCaptor.getValue().startsWith("douyin:auth_state:"));
        assertTrue(valueCaptor.getValue().contains("\"brandId\":10"));
        assertTrue(valueCaptor.getValue().contains("\"redirectArticleId\":20"));
    }

    @Test
    void capabilityAllowsMockModeWhenLiveVerificationBlockedForPendingDomainReview() {
        featureProperties.getImageText().setEnabled(true);
        featureProperties.getImageText().setLiveVerificationBlocked(true);
        featureProperties.getImageText().setLiveVerificationReason("domain_icp_filing_pending");
        clientProperties.setMode("mock");

        DouyinCapabilityVO capability = service.capability();

        assertTrue(capability.isEnabled());
        assertEquals("mock", capability.getMode());
        assertEquals("抖音图文 mock 链路已开放，可用于账号授权、图片选择、提交、失败映射和审核状态流程验证。", capability.getDescription());
        assertFalse(capability.isLiveVerificationBlocked());
    }

    @Test
    void capabilityExposesLiveVerificationBlockedForRealModePendingDomainReview() {
        featureProperties.getImageText().setEnabled(true);
        featureProperties.getImageText().setLiveVerificationBlocked(true);
        featureProperties.getImageText().setLiveVerificationReason("domain_icp_filing_pending");
        clientProperties.setMode("real");

        DouyinCapabilityVO capability = service.capability();

        assertTrue(capability.isEnabled());
        assertTrue(capability.isLiveVerificationBlocked());
        assertEquals("domain_icp_filing_pending", capability.getLiveVerificationReason());
        assertTrue(capability.getDescription().contains("真实图文提交/审核联调暂不可用"));
    }

    @Test
    void handleCallback_success_insertsNewAccountAndConsumesState() {
        String state = "state-ok";
        when(redisTemplate.opsForValue().get("douyin:auth_state:" + state))
                .thenReturn("{\"brandId\":10,\"redirectArticleId\":20,\"createdAt\":\"now\"}");
        when(douyinClient.exchangeCodeForToken(any())).thenReturn(token("open-1", "video.create.bind"));
        when(cipherService.encryptForStorage("access-token")).thenReturn("ENC:access");
        when(cipherService.encryptForStorage("refresh-token")).thenReturn("ENC:refresh");
        when(selfMediaAccountMapper.selectOne(any())).thenReturn(null);

        String redirect = service.handleCallback("code", state);

        verify(redisTemplate).delete("douyin:auth_state:" + state);
        ArgumentCaptor<DouyinCodeTokenRequest> tokenRequestCaptor = ArgumentCaptor.forClass(DouyinCodeTokenRequest.class);
        verify(douyinClient).exchangeCodeForToken(tokenRequestCaptor.capture());
        assertEquals("client-key", tokenRequestCaptor.getValue().getClientKey());
        assertEquals("client-secret", tokenRequestCaptor.getValue().getClientSecret());
        assertEquals("code", tokenRequestCaptor.getValue().getCode());

        ArgumentCaptor<SelfMediaAccount> accountCaptor = ArgumentCaptor.forClass(SelfMediaAccount.class);
        verify(selfMediaAccountMapper).insert(accountCaptor.capture());
        SelfMediaAccount account = accountCaptor.getValue();
        assertEquals(10L, account.getBrandId());
        assertEquals("douyin", account.getPlatform());
        assertEquals("open-1", account.getPlatformAccountId());
        assertEquals("ENC:access", account.getAccessTokenCipher());
        assertEquals("ENC:refresh", account.getRefreshTokenCipher());
        assertEquals("active", account.getStatus());
        assertNotNull(account.getAccessTokenExpiresAt());
        assertNotNull(account.getRefreshTokenExpiresAt());
        assertTrue(account.getScopeJson().contains("\"raw\":\"video.create.bind\""));
        assertTrue(account.getScopeJson().contains("\"list\":[\"video.create.bind\"]"));
        assertTrue(account.getExtraJson().contains("\"open_id\":\"open-1\""));

        assertTrue(redirect.contains("platform=douyin"));
        assertTrue(redirect.contains("douyinAuth=success"));
        assertTrue(redirect.contains("brandId=10"));
        assertTrue(redirect.contains("articleId=20"));
        assertTrue(redirect.contains("accountStatus=active"));
        assertTrue(redirect.contains("platformAccountId=open-1"));
    }

    @Test
    void handleCallback_existingAccount_updatesInsteadOfInsert() {
        String state = "state-existing";
        SelfMediaAccount existing = new SelfMediaAccount();
        existing.setId(99L);
        existing.setCreatedAt(java.time.LocalDateTime.now().minusDays(1));
        when(redisTemplate.opsForValue().get("douyin:auth_state:" + state))
                .thenReturn("{\"brandId\":10,\"redirectArticleId\":null,\"createdAt\":\"now\"}");
        when(douyinClient.exchangeCodeForToken(any())).thenReturn(token("open-1", "video.create.bind"));
        when(selfMediaAccountMapper.selectOne(any())).thenReturn(existing);
        when(cipherService.encryptForStorage(any())).thenAnswer(invocation -> "ENC:" + invocation.getArgument(0));

        service.handleCallback("code", state);

        verify(selfMediaAccountMapper).updateById(existing);
        verify(selfMediaAccountMapper, never()).insert(any());
        assertEquals(99L, existing.getId());
        assertEquals("active", existing.getStatus());
    }

    @Test
    void handleCallback_missingRequiredScope_savesDisabledAccountAndRedirectsScopeMissing() {
        String state = "state-scope-missing";
        when(redisTemplate.opsForValue().get("douyin:auth_state:" + state))
                .thenReturn("{\"brandId\":10,\"redirectArticleId\":null,\"createdAt\":\"now\"}");
        when(douyinClient.exchangeCodeForToken(any())).thenReturn(token("open-1", "user_info"));
        when(cipherService.encryptForStorage(any())).thenAnswer(invocation -> "ENC:" + invocation.getArgument(0));
        when(selfMediaAccountMapper.selectOne(any())).thenReturn(null);

        String redirect = service.handleCallback("code", state);

        ArgumentCaptor<SelfMediaAccount> accountCaptor = ArgumentCaptor.forClass(SelfMediaAccount.class);
        verify(selfMediaAccountMapper).insert(accountCaptor.capture());
        assertEquals("disabled", accountCaptor.getValue().getStatus());
        assertTrue(accountCaptor.getValue().getLastAuthError().contains("douyin scope missing"));
        assertTrue(redirect.contains("douyinAuth=scope_missing"));
        assertTrue(redirect.contains("accountStatus=disabled"));
    }

    @Test
    void handleCallback_invalidState_throwsAndDoesNotCallDouyin() {
        when(redisTemplate.opsForValue().get("douyin:auth_state:expired")).thenReturn(null);

        assertThrows(BizException.class, () -> service.handleCallback("code", "expired"));

        verify(redisTemplate).delete("douyin:auth_state:expired");
        verify(douyinClient, never()).exchangeCodeForToken(any());
    }

    @Test
    void handleCallback_lookupUsesPlatformAndPlatformAccountIdForCrossPlatformSafety() {
        String state = "state-cross-platform";
        when(redisTemplate.opsForValue().get("douyin:auth_state:" + state))
                .thenReturn("{\"brandId\":10,\"redirectArticleId\":null,\"createdAt\":\"now\"}");
        when(douyinClient.exchangeCodeForToken(any())).thenReturn(token("same-id-as-wechat", "video.create.bind"));
        when(cipherService.encryptForStorage(any())).thenAnswer(invocation -> "ENC:" + invocation.getArgument(0));
        when(selfMediaAccountMapper.selectOne(any())).thenReturn(null);

        service.handleCallback("code", state);

        ArgumentCaptor<LambdaQueryWrapper<SelfMediaAccount>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(selfMediaAccountMapper).selectOne(wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("platform"));
        assertTrue(sqlSegment.contains("platform_account_id"));
        verify(selfMediaAccountMapper).insert(any(SelfMediaAccount.class));
    }

    @Test
    void errorRedirect_encodesErrorMessage() {
        String redirect = service.errorRedirect("access_denied", "用户 拒绝授权");

        assertTrue(redirect.contains("platform=douyin"));
        assertTrue(redirect.contains("douyinAuth=callback_failed"));
        assertTrue(redirect.contains("errorCode=access_denied"));
        assertTrue(redirect.contains("errorMessage=%E7%94%A8%E6%88%B7%20%E6%8B%92%E7%BB%9D%E6%8E%88%E6%9D%83"));
    }

    private DouyinTokenResponse token(String openId, String scope) {
        return DouyinTokenResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .openId(openId)
                .expiresIn(7200L)
                .refreshExpiresIn(2592000L)
                .scope(scope)
                .errorCode(0L)
                .message("success")
                .build();
    }
}
