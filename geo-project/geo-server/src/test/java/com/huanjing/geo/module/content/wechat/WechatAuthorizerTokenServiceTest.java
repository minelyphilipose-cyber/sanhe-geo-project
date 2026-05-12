package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatAuthorizerTokenServiceTest {
    private static final String APPID = "wx-authorizer";
    private static final String TOKEN_KEY = "wechat:authorizer_access_token:" + APPID;

    private final SelfMediaAccountMapper accountMapper = mock(SelfMediaAccountMapper.class);
    private final WechatOpenPlatformProperties properties = new WechatOpenPlatformProperties();
    private final WechatComponentAccessTokenService componentAccessTokenService = mock(WechatComponentAccessTokenService.class);
    private final WechatOpenPlatformClient openPlatformClient = mock(WechatOpenPlatformClient.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final MpCredentialCipherService cipherService = mock(MpCredentialCipherService.class);
    private final Map<String, String> redis = new ConcurrentHashMap<>();

    private WechatAuthorizerTokenService service;

    @BeforeEach
    void setUp() {
        properties.setComponentAppid("component-appid");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(inv -> redis.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            redis.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0, String.class);
            String value = inv.getArgument(1, String.class);
            return redis.putIfAbsent(key, value) == null;
        });
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> redis.remove(inv.getArgument(0, String.class)) != null);
        when(cipherService.decrypt("refresh-cipher")).thenReturn("refresh-token");
        when(cipherService.encryptForStorage("refresh-token-2")).thenReturn("refresh-cipher-2");
        when(componentAccessTokenService.getAccessToken()).thenReturn("component-token");

        service = new WechatAuthorizerTokenService(
                accountMapper,
                properties,
                componentAccessTokenService,
                openPlatformClient,
                redisTemplate,
                cipherService
        );
    }

    @Test
    void getAccessToken_refreshTokenExpired_marksAccountExpired() {
        SelfMediaAccount account = account();
        when(openPlatformClient.refreshAuthorizerToken("component-token", "component-appid", APPID, "refresh-token"))
                .thenThrow(new BizException(61023, "refresh token invalid"));

        BizException ex = assertThrows(BizException.class, () -> service.getAccessToken(account));

        assertEquals(61023, ex.getCode());
        assertEquals("expired", account.getStatus());
        assertEquals("wechat refresh token expired, reauthorization required", account.getLastAuthError());
        assertNotNull(account.getLastAuthCheckedAt());
        verify(accountMapper).updateById(account);
        assertEquals(null, redis.get(TOKEN_KEY));
    }

    @Test
    void getAccessToken_concurrentCalls_refreshesOnlyOnce() throws Exception {
        SelfMediaAccount account = account();
        when(openPlatformClient.refreshAuthorizerToken("component-token", "component-appid", APPID, "refresh-token"))
                .thenAnswer(inv -> {
                    Thread.sleep(50);
                    return new WechatOpenPlatformClient.AuthorizerTokenResult("access-token", "refresh-token-2", 7200);
                });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return service.getAccessToken(account);
            });
        }
        List<Future<String>> futures = tasks.stream().map(executor::submit).toList();
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        for (Future<String> future : futures) {
            assertEquals("access-token", future.get(5, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        verify(openPlatformClient, times(1))
                .refreshAuthorizerToken("component-token", "component-appid", APPID, "refresh-token");
        assertEquals("access-token", redis.get(TOKEN_KEY));
        assertEquals("refresh-cipher-2", account.getRefreshTokenCipher());
        verify(accountMapper, times(1)).updateById(account);
    }

    private SelfMediaAccount account() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(1L);
        account.setPlatform("wechat_mp");
        account.setPlatformAccountId(APPID);
        account.setStatus("active");
        account.setRefreshTokenCipher("refresh-cipher");
        return account;
    }
}
