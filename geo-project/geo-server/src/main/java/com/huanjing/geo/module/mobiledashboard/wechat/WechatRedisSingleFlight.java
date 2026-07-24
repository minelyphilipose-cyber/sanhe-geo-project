package com.huanjing.geo.module.mobiledashboard.wechat;

import com.huanjing.geo.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@Slf4j
public class WechatRedisSingleFlight {
    static final Duration LOCK_TTL = Duration.ofSeconds(60);
    static final int WAIT_ATTEMPTS = 180;
    static final long WAIT_INTERVAL_MILLIS = 250L;

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Sleeper sleeper;

    public WechatRedisSingleFlight(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Thread::sleep);
    }

    WechatRedisSingleFlight(StringRedisTemplate redisTemplate, Sleeper sleeper) {
        this.redisTemplate = redisTemplate;
        this.sleeper = sleeper;
    }

    public String getOrRefresh(String cacheKey,
                               String lockKey,
                               Supplier<CacheValue> refreshSupplier) {
        for (int attempt = 0; attempt <= WAIT_ATTEMPTS; attempt++) {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            String ownerToken = UUID.randomUUID().toString();
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, ownerToken, LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                return refreshAsOwner(cacheKey, lockKey, ownerToken, refreshSupplier);
            }
            if (attempt == WAIT_ATTEMPTS) {
                break;
            }
            sleep();
        }
        throw new BizException(503, "wechat credential refresh timed out");
    }

    private String refreshAsOwner(String cacheKey,
                                  String lockKey,
                                  String ownerToken,
                                  Supplier<CacheValue> refreshSupplier) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            CacheValue refreshed = refreshSupplier.get();
            if (refreshed == null || !StringUtils.hasText(refreshed.value()) || refreshed.ttl() == null
                    || refreshed.ttl().isZero() || refreshed.ttl().isNegative()) {
                throw new BizException(502, "wechat credential refresh returned an invalid value");
            }
            redisTemplate.opsForValue().set(cacheKey, refreshed.value(), refreshed.ttl());
            return refreshed.value();
        } finally {
            try {
                redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), ownerToken);
            } catch (RuntimeException ex) {
                log.warn("Unable to release WeChat credential refresh lock key={}", lockKey, ex);
            }
        }
    }

    private void sleep() {
        try {
            sleeper.sleep(WAIT_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(503, "wechat credential refresh was interrupted");
        }
    }

    public record CacheValue(String value, Duration ttl) {
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
