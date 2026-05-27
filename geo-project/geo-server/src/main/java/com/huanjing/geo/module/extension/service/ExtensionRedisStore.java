package com.huanjing.geo.module.extension.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExtensionRedisStore {

    private static final DefaultRedisScript<String> GET_AND_DELETE_SCRIPT =
            new DefaultRedisScript<>("""
                    local v = redis.call('GET', KEYS[1])
                    if v then
                        redis.call('DEL', KEYS[1])
                        return v
                    end
                    return nil
                    """, String.class);

    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL_SCRIPT =
            new DefaultRedisScript<>("""
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                        redis.call('EXPIRE', KEYS[1], ARGV[1])
                    end
                    return count
                    """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private static final DefaultRedisScript<Long> COMPARE_AND_SET_WITH_TTL_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                        return 1
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public String getAndDelete(String key) {
        return stringRedisTemplate.execute(GET_AND_DELETE_SCRIPT, List.of(key));
    }

    public long incrementWithTtl(String key, Duration ttl) {
        Long count = stringRedisTemplate.execute(
                INCREMENT_WITH_TTL_SCRIPT,
                List.of(key),
                String.valueOf(ttl.toSeconds())
        );
        return count == null ? 0 : count;
    }

    public boolean tryLock(String key, String value, Duration ttl) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public boolean releaseLock(String key, String value) {
        Long released = stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(key), value);
        return released != null && released > 0;
    }

    public boolean compareAndSet(String key, String expectedValue, String newValue, Duration ttl) {
        Long updated = stringRedisTemplate.execute(
                COMPARE_AND_SET_WITH_TTL_SCRIPT,
                List.of(key),
                expectedValue,
                newValue,
                String.valueOf(ttl.toSeconds())
        );
        return updated != null && updated > 0;
    }
}
