package com.huanjing.geo.module.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDashboardRefreshLock {

    private static final String KEY_PREFIX = "geo:dashboard:snapshot:refresh:";
    private static final String VALUE_SEPARATOR = "|";
    private static final Duration LEASE_TTL = Duration.ofMinutes(2);
    private static final Duration RENEW_INTERVAL = Duration.ofSeconds(30);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT =
            new DefaultRedisScript<>(renewScript(), Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(releaseScript(), Long.class);

    private final StringRedisTemplate redisTemplate;
    private final TaskScheduler taskScheduler;

    public Lease tryAcquire(Long projectId) {
        String key = key(projectId);
        String value = UUID.randomUUID() + VALUE_SEPARATOR + LocalDateTime.now();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, LEASE_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return null;
        }

        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean valid = new AtomicBoolean(true);
        try {
            ScheduledFuture<?> renewalTask = taskScheduler.scheduleAtFixedRate(
                    () -> renewSafely(key, value, active, valid),
                    Instant.now().plus(RENEW_INTERVAL),
                    RENEW_INTERVAL
            );
            return new Lease(key, value, active, valid, renewalTask);
        } catch (RuntimeException ex) {
            releaseSafely(key, value);
            throw ex;
        }
    }

    public String getStartedAt(Long projectId) {
        String value = redisTemplate.opsForValue().get(key(projectId));
        if (value == null) {
            return "";
        }
        int separator = value.indexOf(VALUE_SEPARATOR);
        return separator < 0 ? value : value.substring(separator + VALUE_SEPARATOR.length());
    }

    private void renewSafely(String key,
                             String value,
                             AtomicBoolean active,
                             AtomicBoolean valid) {
        if (!active.get()) {
            return;
        }
        try {
            if (!renew(key, value)) {
                valid.set(false);
                log.warn("Dashboard snapshot refresh lock was lost, key={}", key);
            }
        } catch (RuntimeException ex) {
            valid.set(false);
            log.warn("Dashboard snapshot refresh lock renewal failed, key={}", key, ex);
        }
    }

    private boolean renew(String key, String value) {
        Long renewed = redisTemplate.execute(
                RENEW_SCRIPT,
                List.of(key),
                value,
                String.valueOf(LEASE_TTL.toMillis())
        );
        return renewed != null && renewed > 0;
    }

    private void releaseSafely(String key, String value) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), value);
        } catch (RuntimeException ex) {
            log.warn("Dashboard snapshot refresh lock release failed, key={}", key, ex);
        }
    }

    private String key(Long projectId) {
        return KEY_PREFIX + projectId;
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
        private final String value;
        private final AtomicBoolean active;
        private final AtomicBoolean valid;
        private final ScheduledFuture<?> renewalTask;

        private Lease(String key,
                      String value,
                      AtomicBoolean active,
                      AtomicBoolean valid,
                      ScheduledFuture<?> renewalTask) {
            this.key = key;
            this.value = value;
            this.active = active;
            this.valid = valid;
            this.renewalTask = renewalTask;
        }

        public void ensureHeld() {
            if (!active.get() || !valid.get() || !renew(key, value)) {
                valid.set(false);
                throw new IllegalStateException("Dashboard snapshot refresh lock was lost before database write");
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
            releaseSafely(key, value);
        }
    }
}
