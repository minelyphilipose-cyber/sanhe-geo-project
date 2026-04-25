package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleExportCleanupLockService {
    private static final String UNLOCK_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) else return 0 end";

    private final StringRedisTemplate redisTemplate;
    private final PresaleExportProperties properties;

    public boolean tryAcquire(String lockValue) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                    properties.getCleanup().getLockKey(),
                    lockValue,
                    Duration.ofMillis(properties.getCleanup().getLockTtlMs()));
            return Boolean.TRUE.equals(ok);
        } catch (Exception ex) {
            log.warn("Presale export cleanup lock unavailable; skip this cycle", ex);
            return false;
        }
    }

    public void release(String lockValue) {
        try {
            String key = properties.getCleanup().getLockKey();
            redisTemplate.execute(new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class), List.of(key), lockValue);
        } catch (Exception ex) {
            log.warn("Release presale export cleanup lock failed", ex);
        }
    }
}
