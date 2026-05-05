package com.huanjing.geo.module.content.douyin;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.DouyinOpenPlatformProperties;
import com.huanjing.geo.module.content.douyin.client.DouyinClient;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinRefreshAccessTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinTokenResponse;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinAuthException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinClientException;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DouyinTokenService {
    private static final String PLATFORM = "douyin";
    private static final String TOKEN_KEY_PREFIX = "douyin:access_token:";
    private static final String LOCK_KEY_PREFIX = "douyin:refresh_lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final int REFRESH_MARGIN_SECONDS = 3600;

    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final DouyinOpenPlatformProperties properties;
    private final DouyinClient douyinClient;
    private final StringRedisTemplate redisTemplate;
    private final MpCredentialCipherService cipherService;

    public String getAccessToken(SelfMediaAccount account) {
        requireDouyinAccount(account);
        if ("expired".equals(account.getStatus())) {
            throw new BizException(401, "douyin account expired, please re-authorize");
        }
        LocalDateTime now = LocalDateTime.now();
        if (account.getRefreshTokenExpiresAt() != null && !account.getRefreshTokenExpiresAt().isAfter(now)) {
            markExpired(account, "refresh_token expired");
            throw new BizException(401, "douyin refresh_token expired");
        }
        String openId = require(account.getPlatformAccountId(), "douyin open_id missing");
        String tokenKey = TOKEN_KEY_PREFIX + openId;
        String cached = redisTemplate.opsForValue().get(tokenKey);
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        String reusableDbToken = reusableDbAccessToken(account, now);
        if (StringUtils.hasText(reusableDbToken)) {
            cacheAccessToken(tokenKey, reusableDbToken, secondsUntil(now, account.getAccessTokenExpiresAt()));
            return reusableDbToken;
        }
        return refreshWithLock(account, tokenKey);
    }

    public String getAccessToken(Long selfMediaAccountId) {
        SelfMediaAccount account = selfMediaAccountMapper.selectById(selfMediaAccountId);
        if (account == null) {
            throw new BizException(404, "self media account not found");
        }
        return getAccessToken(account);
    }

    public void evictAccessToken(SelfMediaAccount account) {
        requireDouyinAccount(account);
        String openId = require(account.getPlatformAccountId(), "douyin open_id missing");
        redisTemplate.delete(TOKEN_KEY_PREFIX + openId);
    }

    private String refreshWithLock(SelfMediaAccount account, String tokenKey) {
        String openId = account.getPlatformAccountId();
        String lockKey = LOCK_KEY_PREFIX + openId;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            sleepBriefly();
            String cached = redisTemplate.opsForValue().get(tokenKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            throw new BizException(429, "douyin token refreshing");
        }
        try {
            String cached = redisTemplate.opsForValue().get(tokenKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            LocalDateTime now = LocalDateTime.now();
            String reusableDbToken = reusableDbAccessToken(account, now);
            if (StringUtils.hasText(reusableDbToken)) {
                cacheAccessToken(tokenKey, reusableDbToken, secondsUntil(now, account.getAccessTokenExpiresAt()));
                return reusableDbToken;
            }
            String oldRefreshToken = cipherService.decrypt(account.getRefreshTokenCipher());
            DouyinTokenResponse result;
            try {
                result = douyinClient.refreshAccessToken(DouyinRefreshAccessTokenRequest.builder()
                        .clientKey(require(properties.getClientKey(), "douyin client key missing"))
                        .refreshToken(oldRefreshToken)
                        .build());
            } catch (DouyinClientException ex) {
                handleRefreshException(account, ex);
                throw ex;
            }
            String accessToken = require(result.getAccessToken(), "douyin refreshed access_token missing");
            updateAccountAfterRefresh(account, result, oldRefreshToken, now);
            cacheAccessToken(tokenKey, accessToken, result.getExpiresIn());
            return accessToken;
        } finally {
            String current = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(current)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    private String reusableDbAccessToken(SelfMediaAccount account, LocalDateTime now) {
        if (!StringUtils.hasText(account.getAccessTokenCipher()) || account.getAccessTokenExpiresAt() == null) {
            return null;
        }
        if (account.getAccessTokenExpiresAt().isBefore(now.plusSeconds(REFRESH_MARGIN_SECONDS))) {
            return null;
        }
        return cipherService.decrypt(account.getAccessTokenCipher());
    }

    private void updateAccountAfterRefresh(SelfMediaAccount account,
                                           DouyinTokenResponse result,
                                           String oldRefreshToken,
                                           LocalDateTime now) {
        account.setAccessTokenCipher(cipherService.encryptForStorage(result.getAccessToken()));
        account.setAccessTokenExpiresAt(plusSeconds(now, result.getExpiresIn()));
        if (StringUtils.hasText(result.getRefreshToken()) && !result.getRefreshToken().equals(oldRefreshToken)) {
            account.setRefreshTokenCipher(cipherService.encryptForStorage(result.getRefreshToken()));
            account.setRefreshTokenExpiresAt(plusSeconds(now, result.getRefreshExpiresIn()));
        }
        account.setStatus("active");
        account.setLastAuthCheckedAt(now);
        account.setLastAuthError(null);
        account.setUpdatedAt(now);
        selfMediaAccountMapper.updateById(account);
    }

    private void handleRefreshException(SelfMediaAccount account, DouyinClientException ex) {
        if (ex instanceof DouyinAuthException && Long.valueOf(10010L).equals(ex.getErrorCode())) {
            markExpired(account, "refresh_token expired");
            return;
        }
        log.warn("Douyin token refresh failed accountId={} openId={} errorCode={} message={}",
                account.getId(), account.getPlatformAccountId(), ex.getErrorCode(), ex.getMessage());
    }

    private void markExpired(SelfMediaAccount account, String reason) {
        if ("expired".equals(account.getStatus())) {
            return;
        }
        account.setStatus("expired");
        account.setLastAuthCheckedAt(LocalDateTime.now());
        account.setLastAuthError(reason);
        account.setUpdatedAt(LocalDateTime.now());
        selfMediaAccountMapper.updateById(account);
    }

    private void cacheAccessToken(String tokenKey, String accessToken, Long expiresInSeconds) {
        if (!StringUtils.hasText(accessToken) || expiresInSeconds == null) {
            return;
        }
        long ttl = Math.max(60, expiresInSeconds - REFRESH_MARGIN_SECONDS);
        redisTemplate.opsForValue().set(tokenKey, accessToken, Duration.ofSeconds(ttl));
    }

    private long secondsUntil(LocalDateTime now, LocalDateTime expiresAt) {
        return Duration.between(now, expiresAt).getSeconds();
    }

    private LocalDateTime plusSeconds(LocalDateTime base, Long seconds) {
        return seconds == null ? null : base.plusSeconds(seconds);
    }

    private void requireDouyinAccount(SelfMediaAccount account) {
        if (account == null) {
            throw new BizException(404, "self media account not found");
        }
        if (!PLATFORM.equals(account.getPlatform())) {
            throw new BizException(400, "not douyin account");
        }
        require(account.getPlatformAccountId(), "douyin open_id missing");
        require(account.getRefreshTokenCipher(), "douyin refresh_token missing");
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
