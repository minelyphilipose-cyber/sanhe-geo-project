package com.huanjing.geo.module.mobiledashboard.wechat;

import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class WechatOfficialAccessTokenService {
    private static final String TOKEN_KEY_PREFIX = "wechat:mobile_dashboard:official_access_token:";
    private static final String LOCK_KEY_PREFIX = "wechat:mobile_dashboard:official_access_token_lock:";
    private static final int REFRESH_MARGIN_SECONDS = 300;

    private final MobileDashboardWechatJsSdkProperties properties;
    private final WechatOfficialAccountClient client;
    private final StringRedisTemplate redisTemplate;
    private final WechatRedisSingleFlight singleFlight;

    public String getAccessToken() {
        String appId = require(properties.getAppId(), "WeChat JS-SDK AppID is missing");
        return singleFlight.getOrRefresh(
                TOKEN_KEY_PREFIX + appId,
                LOCK_KEY_PREFIX + appId,
                () -> fetchAccessToken(appId)
        );
    }

    public void evict() {
        if (StringUtils.hasText(properties.getAppId())) {
            redisTemplate.delete(TOKEN_KEY_PREFIX + properties.getAppId().trim());
        }
    }

    private WechatRedisSingleFlight.CacheValue fetchAccessToken(String appId) {
        WechatOfficialAccountClient.AccessTokenResult result = client.getAccessToken(
                appId,
                require(properties.getAppSecret(), "WeChat JS-SDK AppSecret is missing")
        );
        int ttl = Math.max(60, result.expiresIn() - REFRESH_MARGIN_SECONDS);
        log.info("WeChat official account access token refreshed ttlSeconds={}", ttl);
        return new WechatRedisSingleFlight.CacheValue(result.accessToken(), Duration.ofSeconds(ttl));
    }

    private String require(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(503, message);
        }
        return value.trim();
    }

}
