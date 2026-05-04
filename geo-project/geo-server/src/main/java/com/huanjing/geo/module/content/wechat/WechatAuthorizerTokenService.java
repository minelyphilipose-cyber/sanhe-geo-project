package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.entity.MpAccount;
import com.huanjing.geo.module.content.mapper.MpAccountMapper;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WechatAuthorizerTokenService {
    private static final String TOKEN_KEY_PREFIX = "wechat:authorizer_access_token:";
    private static final String LOCK_KEY_PREFIX = "wechat:refresh_lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final int REFRESH_MARGIN_SECONDS = 300;

    private final MpAccountMapper mpAccountMapper;
    private final WechatOpenPlatformProperties properties;
    private final WechatComponentAccessTokenService componentAccessTokenService;
    private final WechatOpenPlatformClient openPlatformClient;
    private final StringRedisTemplate redisTemplate;
    private final MpCredentialCipherService cipherService;

    public String getAccessToken(MpAccount account) {
        String appid = require(account.getAuthorizerAppid(), "authorizer appid missing");
        String tokenKey = TOKEN_KEY_PREFIX + appid;
        String cached = redisTemplate.opsForValue().get(tokenKey);
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        return refreshWithLock(account, tokenKey);
    }

    public String getAccessToken(Long mpAccountId) {
        MpAccount account = mpAccountMapper.selectById(mpAccountId);
        if (account == null) {
            throw new BizException(404, "mp account not found");
        }
        return getAccessToken(account);
    }

    public void evictAccessToken(MpAccount account) {
        String appid = require(account.getAuthorizerAppid(), "authorizer appid missing");
        redisTemplate.delete(TOKEN_KEY_PREFIX + appid);
    }

    private String refreshWithLock(MpAccount account, String tokenKey) {
        String appid = account.getAuthorizerAppid();
        String lockKey = LOCK_KEY_PREFIX + appid;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            sleepBriefly();
            String cached = redisTemplate.opsForValue().get(tokenKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            throw new BizException(429, "wechat authorizer token refreshing");
        }
        try {
            String cached = redisTemplate.opsForValue().get(tokenKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            String refreshToken = cipherService.decrypt(account.getAuthorizerRefreshTokenCipher());
            String componentToken = componentAccessTokenService.getAccessToken();
            WechatOpenPlatformClient.AuthorizerTokenResult result =
                    openPlatformClient.refreshAuthorizerToken(
                            componentToken,
                            properties.getComponentAppid(),
                            appid,
                            refreshToken
                    );
            int ttl = Math.max(60, result.expiresIn() - REFRESH_MARGIN_SECONDS);
            redisTemplate.opsForValue().set(tokenKey, result.authorizerAccessToken(), Duration.ofSeconds(ttl));
            if (StringUtils.hasText(result.authorizerRefreshToken())
                    && !result.authorizerRefreshToken().equals(refreshToken)) {
                account.setAuthorizerRefreshTokenCipher(cipherService.encryptForStorage(result.authorizerRefreshToken()));
                account.setLastAuthCheckedAt(LocalDateTime.now());
                account.setLastAuthError(null);
                mpAccountMapper.updateById(account);
            }
            return result.authorizerAccessToken();
        } finally {
            String current = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(current)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    private String require(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(500, message);
        }
        return value;
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
