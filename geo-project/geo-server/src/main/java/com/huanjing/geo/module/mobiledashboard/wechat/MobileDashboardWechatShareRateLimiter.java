package com.huanjing.geo.module.mobiledashboard.wechat;

import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MobileDashboardWechatShareRateLimiter {
    private static final DefaultRedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local current = tonumber(redis.call('GET', KEYS[1]) or '0') " +
                    "local limit = tonumber(ARGV[1]) " +
                    "if current >= limit then return 0 end " +
                    "current = redis.call('INCR', KEYS[1]) " +
                    "if current == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) end " +
                    "return current <= limit and 1 or 0",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final MobileDashboardWechatJsSdkProperties properties;

    public void enforceConfig(Long shareId, String clientIp) {
        enforce("config", shareId, clientIp, properties.getSignatureRateLimitPerMinute());
    }

    public void enforceClientError(Long shareId, String clientIp) {
        enforce("client_error", shareId, clientIp, properties.getErrorReportRateLimitPerMinute());
    }

    private void enforce(String operation, Long shareId, String clientIp, int limit) {
        String minute = String.valueOf(Instant.now().getEpochSecond() / 60);
        String key = "wechat:mobile_dashboard:rate:" + operation + ":" + shareId + ":" + hash(clientIp) + ":" + minute;
        try {
            Long allowed = redisTemplate.execute(
                    LIMIT_SCRIPT,
                    List.of(key),
                    String.valueOf(Math.max(1, limit)),
                    "90"
            );
            if (allowed != null && allowed > 0) {
                return;
            }
        } catch (RuntimeException ex) {
            log.warn("Mobile dashboard WeChat dedicated rate limiter unavailable operation={} shareId={}",
                    operation, shareId, ex);
            return;
        }
        log.warn("Mobile dashboard WeChat dedicated rate limit exceeded operation={} shareId={}",
                operation, shareId);
        throw new BizException(429, "操作过于频繁，请稍后再试");
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 8);
        } catch (Exception ex) {
            return "unknown";
        }
    }
}
