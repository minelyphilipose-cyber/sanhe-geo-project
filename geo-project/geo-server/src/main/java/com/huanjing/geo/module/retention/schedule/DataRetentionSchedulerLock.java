package com.huanjing.geo.module.retention.schedule;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataRetentionSchedulerLock {

    private static final DefaultRedisScript<Long> RENEW_SCRIPT =
            new DefaultRedisScript<>(renewScript(), Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(releaseScript(), Long.class);

    private final StringRedisTemplate redisTemplate;
    private final DataRetentionProperties properties;
    private final TaskScheduler taskScheduler;

    public Lease tryAcquire() {
        String key = properties.getScheduler().getLockKey();
        String owner = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(Math.max(60L, properties.getScheduler().getLockTtlSeconds()));
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, owner, ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            return null;
        }
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean valid = new AtomicBoolean(true);
        Duration renewInterval = Duration.ofMillis(Math.max(20_000L, ttl.toMillis() / 3L));
        try {
            ScheduledFuture<?> renewalTask = taskScheduler.scheduleAtFixedRate(
                    () -> renewSafely(key, owner, ttl, active, valid),
                    Instant.now().plus(renewInterval),
                    renewInterval
            );
            return new Lease(key, owner, ttl, active, valid, renewalTask);
        } catch (RuntimeException ex) {
            release(key, owner);
            throw ex;
        }
    }

    private void renewSafely(String key,
                             String owner,
                             Duration ttl,
                             AtomicBoolean active,
                             AtomicBoolean valid) {
        if (!active.get()) {
            return;
        }
        try {
            if (!renew(key, owner, ttl)) {
                valid.set(false);
                log.warn("Data retention scheduler lock was lost, key={}", key);
            }
        } catch (RuntimeException ex) {
            valid.set(false);
            log.warn("Data retention scheduler lock renewal failed, key={}", key, ex);
        }
    }

    private boolean renew(String key, String owner, Duration ttl) {
        Long renewed = redisTemplate.execute(
                RENEW_SCRIPT, List.of(key), owner, String.valueOf(ttl.toMillis()));
        return renewed != null && renewed > 0;
    }

    private void release(String key, String owner) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), owner);
        } catch (RuntimeException ex) {
            log.warn("Data retention scheduler lock release failed, key={}", key, ex);
        }
    }

    static String renewScript() {
        return """
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    return redis.call('PEXPIRE', KEYS[1], ARGV[2])
                end
                return 0
                """;
    }

    static String releaseScript() {
        return """
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    return redis.call('DEL', KEYS[1])
                end
                return 0
                """;
    }

    public final class Lease implements AutoCloseable {
        private final String key;
        private final String owner;
        private final Duration ttl;
        private final AtomicBoolean active;
        private final AtomicBoolean valid;
        private final ScheduledFuture<?> renewalTask;

        private Lease(String key,
                      String owner,
                      Duration ttl,
                      AtomicBoolean active,
                      AtomicBoolean valid,
                      ScheduledFuture<?> renewalTask) {
            this.key = key;
            this.owner = owner;
            this.ttl = ttl;
            this.active = active;
            this.valid = valid;
            this.renewalTask = renewalTask;
        }

        public void ensureHeld() {
            if (!active.get() || !valid.get() || !renew(key, owner, ttl)) {
                valid.set(false);
                throw new IllegalStateException("Data retention scheduler lock was lost");
            }
        }

        @Override
        public void close() {
            if (!active.compareAndSet(true, false)) {
                return;
            }
            if (renewalTask != null) {
                renewalTask.cancel(false);
            }
            release(key, owner);
        }
    }
}
