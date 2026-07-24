package com.huanjing.geo.module.mobiledashboard.wechat;

import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class WechatJsapiTicketService {
    private static final String TICKET_KEY_PREFIX = "wechat:mobile_dashboard:jsapi_ticket:";
    private static final String LOCK_KEY_PREFIX = "wechat:mobile_dashboard:jsapi_ticket_lock:";
    private static final int REFRESH_MARGIN_SECONDS = 300;
    private static final Set<Integer> TOKEN_EXPIRED_CODES = Set.of(40001, 40014, 42001);

    private final MobileDashboardWechatJsSdkProperties properties;
    private final WechatOfficialAccountClient client;
    private final WechatOfficialAccessTokenService accessTokenService;
    private final StringRedisTemplate redisTemplate;
    private final WechatRedisSingleFlight singleFlight;

    public String getTicket() {
        String appId = requireAppId();
        return singleFlight.getOrRefresh(
                TICKET_KEY_PREFIX + appId,
                LOCK_KEY_PREFIX + appId,
                this::fetchTicket
        );
    }

    public void evict() {
        if (StringUtils.hasText(properties.getAppId())) {
            redisTemplate.delete(TICKET_KEY_PREFIX + properties.getAppId().trim());
        }
    }

    private WechatRedisSingleFlight.CacheValue fetchTicket() {
        WechatOfficialAccountClient.JsapiTicketResult result = fetchTicketWithCredentialRetry();
        int ttl = Math.max(60, result.expiresIn() - REFRESH_MARGIN_SECONDS);
        log.info("WeChat JSAPI ticket refreshed ttlSeconds={}", ttl);
        return new WechatRedisSingleFlight.CacheValue(result.ticket(), Duration.ofSeconds(ttl));
    }

    private WechatOfficialAccountClient.JsapiTicketResult fetchTicketWithCredentialRetry() {
        String accessToken = accessTokenService.getAccessToken();
        try {
            return client.getJsapiTicket(accessToken);
        } catch (BizException ex) {
            if (!TOKEN_EXPIRED_CODES.contains(ex.getCode())) {
                throw ex;
            }
            accessTokenService.evict();
            return client.getJsapiTicket(accessTokenService.getAccessToken());
        }
    }

    private String requireAppId() {
        if (!StringUtils.hasText(properties.getAppId())) {
            throw new BizException(503, "WeChat JS-SDK AppID is missing");
        }
        return properties.getAppId().trim();
    }

}
