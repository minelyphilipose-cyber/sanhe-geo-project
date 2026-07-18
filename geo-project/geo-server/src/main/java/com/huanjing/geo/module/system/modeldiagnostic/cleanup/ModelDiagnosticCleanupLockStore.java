package com.huanjing.geo.module.system.modeldiagnostic.cleanup;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ModelDiagnosticCleanupLockStore {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquire(String key, String ownerToken, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(key, ownerToken, ttl));
    }

    public boolean release(String key, String ownerToken) {
        Long result = redisTemplate.execute(
                RELEASE_SCRIPT, List.of(key), ownerToken);
        return Long.valueOf(1L).equals(result);
    }
}
