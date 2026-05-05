package com.huanjing.geo.module.content.douyin;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.DouyinOpenPlatformProperties;
import com.huanjing.geo.module.content.douyin.client.DouyinClient;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinRefreshAccessTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinTokenResponse;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinAuthException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinServerException;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DouyinTokenServiceTest {
    private static final String TOKEN_KEY = "douyin:access_token:open-1";
    private static final String LOCK_KEY = "douyin:refresh_lock:open-1";

    private SelfMediaAccountMapper selfMediaAccountMapper;
    private DouyinOpenPlatformProperties properties;
    private DouyinClient douyinClient;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private MpCredentialCipherService cipherService;
    private DouyinTokenService service;

    @BeforeEach
    void setUp() {
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        properties = new DouyinOpenPlatformProperties();
        properties.setClientKey("client-key");
        douyinClient = mock(DouyinClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        cipherService = mock(MpCredentialCipherService.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new DouyinTokenService(
                selfMediaAccountMapper,
                properties,
                douyinClient,
                redisTemplate,
                cipherService
        );
    }

    @Test
    void getAccessToken_returnsRedisCachedToken() {
        when(valueOperations.get(TOKEN_KEY)).thenReturn("cached-access");

        String token = service.getAccessToken(account());

        assertEquals("cached-access", token);
        verifyNoInteractions(douyinClient, cipherService);
    }

    @Test
    void getAccessToken_usesReusableDbTokenAndWritesRedis() {
        SelfMediaAccount account = account();
        account.setAccessTokenCipher("ENC:db-access");
        account.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(2));
        when(valueOperations.get(TOKEN_KEY)).thenReturn(null);
        when(cipherService.decrypt("ENC:db-access")).thenReturn("db-access");

        String token = service.getAccessToken(account);

        assertEquals("db-access", token);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(TOKEN_KEY), eq("db-access"), ttlCaptor.capture());
        assertTrue(ttlCaptor.getValue().toSeconds() >= 3500);
        assertTrue(ttlCaptor.getValue().toSeconds() <= 3600);
        verifyNoInteractions(douyinClient);
    }

    @Test
    void getAccessToken_dbTokenNearExpiryTriggersRefresh() {
        SelfMediaAccount account = account();
        account.setAccessTokenCipher("ENC:old-access");
        account.setAccessTokenExpiresAt(LocalDateTime.now().plusMinutes(30));
        mockLockAcquired(null);
        when(cipherService.decrypt("ENC:refresh")).thenReturn("old-refresh");
        when(cipherService.encryptForStorage("new-access")).thenReturn("ENC:new-access");
        when(douyinClient.refreshAccessToken(any())).thenReturn(token("new-access", "old-refresh"));

        String token = service.getAccessToken(account);

        assertEquals("new-access", token);
        verify(douyinClient).refreshAccessToken(any(DouyinRefreshAccessTokenRequest.class));
        verify(selfMediaAccountMapper).updateById(account);
    }

    @Test
    void refresh_updatesAccessTokenAndKeepsUnchangedRefreshToken() {
        SelfMediaAccount account = account();
        LocalDateTime oldRefreshExpiresAt = account.getRefreshTokenExpiresAt();
        mockLockAcquired(null);
        when(cipherService.decrypt("ENC:refresh")).thenReturn("old-refresh");
        when(cipherService.encryptForStorage("new-access")).thenReturn("ENC:new-access");
        when(douyinClient.refreshAccessToken(any())).thenReturn(token("new-access", "old-refresh"));

        String token = service.getAccessToken(account);

        assertEquals("new-access", token);
        assertEquals("ENC:new-access", account.getAccessTokenCipher());
        assertEquals("ENC:refresh", account.getRefreshTokenCipher());
        assertEquals(oldRefreshExpiresAt, account.getRefreshTokenExpiresAt());
        assertEquals("active", account.getStatus());
        verify(valueOperations).set(TOKEN_KEY, "new-access", Duration.ofSeconds(3600));
    }

    @Test
    void refresh_updatesRefreshTokenOnlyWhenChanged() {
        SelfMediaAccount account = account();
        mockLockAcquired(null);
        when(cipherService.decrypt("ENC:refresh")).thenReturn("old-refresh");
        when(cipherService.encryptForStorage("new-access")).thenReturn("ENC:new-access");
        when(cipherService.encryptForStorage("new-refresh")).thenReturn("ENC:new-refresh");
        when(douyinClient.refreshAccessToken(any())).thenReturn(token("new-access", "new-refresh"));

        String token = service.getAccessToken(account);

        assertEquals("new-access", token);
        assertEquals("ENC:new-refresh", account.getRefreshTokenCipher());
        assertNotNull(account.getRefreshTokenExpiresAt());
        verify(selfMediaAccountMapper).updateById(account);
    }

    @Test
    void refresh_blankRefreshTokenKeepsOldRefreshTokenAndExpiry() {
        SelfMediaAccount account = account();
        LocalDateTime oldRefreshExpiresAt = account.getRefreshTokenExpiresAt();
        mockLockAcquired(null);
        when(cipherService.decrypt("ENC:refresh")).thenReturn("old-refresh");
        when(cipherService.encryptForStorage("new-access")).thenReturn("ENC:new-access");
        when(douyinClient.refreshAccessToken(any())).thenReturn(token("new-access", ""));

        service.getAccessToken(account);

        assertEquals("ENC:refresh", account.getRefreshTokenCipher());
        assertEquals(oldRefreshExpiresAt, account.getRefreshTokenExpiresAt());
    }

    @Test
    void refreshTokenExpired_marksAccountExpiredAndThrows401() {
        SelfMediaAccount account = account();
        account.setRefreshTokenExpiresAt(LocalDateTime.now().minusSeconds(1));

        BizException ex = assertThrows(BizException.class, () -> service.getAccessToken(account));

        assertEquals(401, ex.getCode());
        assertEquals("expired", account.getStatus());
        assertEquals("refresh_token expired", account.getLastAuthError());
        verify(selfMediaAccountMapper).updateById(account);
        verifyNoInteractions(douyinClient);
    }

    @Test
    void getAccessToken_alreadyExpiredAccountThrows401WithoutRedundantUpdate() {
        SelfMediaAccount account = account();
        account.setStatus("expired");

        BizException ex = assertThrows(BizException.class, () -> service.getAccessToken(account));

        assertEquals(401, ex.getCode());
        assertEquals("douyin account expired, please re-authorize", ex.getMessage());
        verify(selfMediaAccountMapper, never()).updateById(any());
        verifyNoInteractions(douyinClient);
    }

    @Test
    void refreshAuthError10010_marksAccountExpiredAndRethrows() {
        SelfMediaAccount account = account();
        mockLockAcquired(null);
        when(cipherService.decrypt("ENC:refresh")).thenReturn("old-refresh");
        when(douyinClient.refreshAccessToken(any()))
                .thenThrow(new DouyinAuthException(200, 10010L, "refresh token expired", "log", false, "{}"));

        assertThrows(DouyinAuthException.class, () -> service.getAccessToken(account));

        assertEquals("expired", account.getStatus());
        assertEquals("refresh_token expired", account.getLastAuthError());
        verify(selfMediaAccountMapper).updateById(account);
    }

    @Test
    void refreshAuthError10013_keepsAccountStatus() {
        SelfMediaAccount account = account();
        mockLockAcquired(null);
        when(cipherService.decrypt("ENC:refresh")).thenReturn("old-refresh");
        when(douyinClient.refreshAccessToken(any()))
                .thenThrow(new DouyinAuthException(200, 10013L, "client key invalid", "log", false, "{}"));

        assertThrows(DouyinAuthException.class, () -> service.getAccessToken(account));

        assertEquals("active", account.getStatus());
        verify(selfMediaAccountMapper, never()).updateById(account);
    }

    @Test
    void refreshServerBusy_keepsAccountStatus() {
        SelfMediaAccount account = account();
        mockLockAcquired(null);
        when(cipherService.decrypt("ENC:refresh")).thenReturn("old-refresh");
        when(douyinClient.refreshAccessToken(any()))
                .thenThrow(new DouyinServerException(500, 10001L, "server busy", "log", true, "{}"));

        assertThrows(DouyinServerException.class, () -> service.getAccessToken(account));

        assertEquals("active", account.getStatus());
        verify(selfMediaAccountMapper, never()).updateById(account);
    }

    @Test
    void refreshLockNotAcquiredAndCacheStillMissingThrows429() {
        SelfMediaAccount account = account();
        when(valueOperations.get(TOKEN_KEY)).thenReturn(null, null);
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(10)))).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.getAccessToken(account));

        assertEquals(429, ex.getCode());
        assertEquals("douyin token refreshing", ex.getMessage());
        verifyNoInteractions(douyinClient);
    }

    @Test
    void refreshLockAcquiredThenRedisHitAvoidsRefresh() {
        SelfMediaAccount account = account();
        when(valueOperations.get(TOKEN_KEY)).thenReturn(null, "second-read-access");
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(10)))).thenReturn(true);
        when(valueOperations.get(LOCK_KEY)).thenReturn(null);

        String token = service.getAccessToken(account);

        assertEquals("second-read-access", token);
        verifyNoInteractions(douyinClient);
    }

    @Test
    void refreshLockValueMismatchDoesNotDeleteLock() {
        SelfMediaAccount account = account();
        when(valueOperations.get(TOKEN_KEY)).thenReturn(null, null);
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(10)))).thenReturn(true);
        when(valueOperations.get(LOCK_KEY)).thenReturn("other-lock");
        when(cipherService.decrypt("ENC:refresh")).thenReturn("old-refresh");
        when(cipherService.encryptForStorage("new-access")).thenReturn("ENC:new-access");
        when(douyinClient.refreshAccessToken(any())).thenReturn(token("new-access", "old-refresh"));

        service.getAccessToken(account);

        verify(redisTemplate, never()).delete(LOCK_KEY);
    }

    @Test
    void refreshLockValueMatchDeletesLock() {
        SelfMediaAccount account = account();
        AtomicReference<String> lockValue = new AtomicReference<>();
        when(valueOperations.get(TOKEN_KEY)).thenReturn(null, null);
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(10))))
                .thenAnswer(invocation -> {
                    lockValue.set(invocation.getArgument(1));
                    return true;
                });
        when(valueOperations.get(LOCK_KEY)).thenAnswer(invocation -> lockValue.get());
        when(cipherService.decrypt("ENC:refresh")).thenReturn("old-refresh");
        when(cipherService.encryptForStorage("new-access")).thenReturn("ENC:new-access");
        when(douyinClient.refreshAccessToken(any())).thenReturn(token("new-access", "old-refresh"));

        service.getAccessToken(account);

        verify(redisTemplate).delete(LOCK_KEY);
    }

    private void mockLockAcquired(String currentLockValue) {
        when(valueOperations.get(TOKEN_KEY)).thenReturn(null, null);
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(10)))).thenReturn(true);
        when(valueOperations.get(LOCK_KEY)).thenReturn(currentLockValue);
    }

    private SelfMediaAccount account() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(1L);
        account.setBrandId(10L);
        account.setPlatform("douyin");
        account.setPlatformAccountId("open-1");
        account.setAccountName("Douyin Test");
        account.setStatus("active");
        account.setRefreshTokenCipher("ENC:refresh");
        account.setRefreshTokenExpiresAt(LocalDateTime.now().plusDays(30));
        return account;
    }

    private DouyinTokenResponse token(String accessToken, String refreshToken) {
        return DouyinTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .openId("open-1")
                .expiresIn(7200L)
                .refreshExpiresIn(2592000L)
                .build();
    }
}
