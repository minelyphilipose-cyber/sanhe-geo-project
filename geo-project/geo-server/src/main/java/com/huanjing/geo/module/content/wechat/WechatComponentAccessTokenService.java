package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WechatComponentAccessTokenService {
    private static final String KEY_PREFIX = "wechat:component_access_token:";
    private static final String LOCK_KEY_PREFIX = "wechat:refresh_lock:component:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final int REFRESH_MARGIN_SECONDS = 300;

    private final WechatOpenPlatformProperties properties;
    private final WechatComponentTicketService ticketService;
    private final WechatOpenPlatformClient openPlatformClient;
    private final StringRedisTemplate redisTemplate;

    public String getAccessToken() {
        String componentAppid = require(properties.getComponentAppid(), "wechat component appid missing");
        String key = KEY_PREFIX + componentAppid;
        String cached = redisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        return refreshWithLock(componentAppid, key);
    }

    private String refreshWithLock(String componentAppid, String tokenKey) {
        String lockKey = LOCK_KEY_PREFIX + componentAppid;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            sleepBriefly();
            String cached = redisTemplate.opsForValue().get(tokenKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            throw new BizException(429, "wechat component token refreshing");
        }
        try {
            String cached = redisTemplate.opsForValue().get(tokenKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            return refresh(componentAppid, tokenKey);
        } finally {
            String current = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(current)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    private String refresh(String componentAppid, String key) {
        String ticket = require(ticketService.getLatestTicket(componentAppid), "wechat component ticket missing");
        String secret = require(properties.getComponentAppSecret(), "wechat component secret missing");
        WechatOpenPlatformClient.ComponentAccessTokenResult result =
                openPlatformClient.getComponentAccessToken(componentAppid, secret, ticket);
        int ttl = Math.max(60, result.expiresIn() - REFRESH_MARGIN_SECONDS);
        redisTemplate.opsForValue().set(key, result.accessToken(), Duration.ofSeconds(ttl));
        return result.accessToken();
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
