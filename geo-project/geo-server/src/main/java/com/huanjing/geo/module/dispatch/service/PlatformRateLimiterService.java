package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PlatformRateLimiterService {

    private static final DateTimeFormatter KEY_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DefaultRedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local rpm=tonumber(redis.call('GET', KEYS[1]) or '0') " +
                    "local tpm=tonumber(redis.call('GET', KEYS[2]) or '0') " +
                    "local rpmLimit=tonumber(ARGV[1]) " +
                    "local tpmLimit=tonumber(ARGV[2]) " +
                    "local tokenCost=tonumber(ARGV[3]) " +
                    "if rpm + 1 > rpmLimit then return 0 end " +
                    "if tpm + tokenCost > tpmLimit then return 0 end " +
                    "redis.call('INCRBY', KEYS[1], 1) " +
                    "redis.call('INCRBY', KEYS[2], tokenCost) " +
                    "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4])) " +
                    "redis.call('EXPIRE', KEYS[2], tonumber(ARGV[4])) " +
                    "return 1",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquire(AiPlatformConfig config, int tokenCost) {
        int rpmLimit = config.getRpmLimit() == null || config.getRpmLimit() <= 0 ? 60 : config.getRpmLimit();
        int tpmLimit = config.getTpmLimit() == null || config.getTpmLimit() <= 0 ? 60000 : config.getTpmLimit();
        String minute = LocalDateTime.now().format(KEY_FMT);
        String rpmKey = "geo:dispatch:limiter:rpm:" + config.getPlatformCode() + ":" + minute;
        String tpmKey = "geo:dispatch:limiter:tpm:" + config.getPlatformCode() + ":" + minute;

        Long result = redisTemplate.execute(
                LIMIT_SCRIPT,
                java.util.List.of(rpmKey, tpmKey),
                String.valueOf(rpmLimit),
                String.valueOf(tpmLimit),
                String.valueOf(Math.max(tokenCost, 1)),
                "120"
        );
        return result != null && result > 0;
    }
}
