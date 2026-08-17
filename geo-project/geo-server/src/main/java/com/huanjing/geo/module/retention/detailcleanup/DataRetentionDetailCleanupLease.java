package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
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
@Slf4j
public class DataRetentionDetailCleanupLease {
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    public enum AcquireCode { ACQUIRED, BUSY, UNAVAILABLE }
    public record AcquireResult(AcquireCode code, Lease lease, RuntimeException error) { }

    private final StringRedisTemplate redis;
    private final DataRetentionProperties properties;
    private final TaskScheduler scheduler;

    public DataRetentionDetailCleanupLease(StringRedisTemplate redis, DataRetentionProperties properties,
                                           TaskScheduler scheduler) {
        this.redis = redis; this.properties = properties; this.scheduler = scheduler;
    }

    public AcquireResult tryAcquire() {
        String key = properties.getDetailCleanup().getLockKey();
        String owner = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getDetailCleanup().getLockTtlSeconds()));
        try {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, owner, ttl))) {
                return new AcquireResult(AcquireCode.BUSY, null, null);
            }
            AtomicBoolean active = new AtomicBoolean(true);
            AtomicBoolean held = new AtomicBoolean(true);
            Duration interval = Duration.ofMillis(Math.max(20_000, ttl.toMillis() / 3));
            ScheduledFuture<?> renewal;
            try {
                renewal = scheduler.scheduleAtFixedRate(() -> renewSafely(key, owner, ttl, active, held),
                        Instant.now().plus(interval), interval);
            } catch (RuntimeException error) {
                release(key, owner); throw error;
            }
            return new AcquireResult(AcquireCode.ACQUIRED,
                    new Lease(key, owner, ttl, active, held, renewal), null);
        } catch (RuntimeException error) {
            return new AcquireResult(AcquireCode.UNAVAILABLE, null, error);
        }
    }

    private void renewSafely(String key, String owner, Duration ttl,
                             AtomicBoolean active, AtomicBoolean held) {
        if (!active.get()) return;
        try { if (!renew(key, owner, ttl)) held.set(false); }
        catch (RuntimeException error) {
            held.set(false);
            log.error("WP8 detail cleanup lease renewal failed key={}", key, error);
        }
    }
    private boolean renew(String key, String owner, Duration ttl) {
        Long value = redis.execute(RENEW, List.of(key), owner, Long.toString(ttl.toMillis()));
        return value != null && value == 1;
    }
    private void release(String key, String owner) {
        try { redis.execute(RELEASE, List.of(key), owner); }
        catch (RuntimeException error) { log.error("WP8 detail cleanup lease release failed key={}", key, error); }
    }

    public final class Lease implements AutoCloseable {
        private final String key; private final String owner; private final Duration ttl;
        private final AtomicBoolean active; private final AtomicBoolean held;
        private final ScheduledFuture<?> renewal;
        private Lease(String key, String owner, Duration ttl, AtomicBoolean active,
                      AtomicBoolean held, ScheduledFuture<?> renewal) {
            this.key=key; this.owner=owner; this.ttl=ttl; this.active=active; this.held=held; this.renewal=renewal;
        }
        public void ensureHeld() {
            if (!active.get() || !held.get()) throw lost();
            try { if (!renew(key, owner, ttl)) throw lost(); }
            catch (DataRetentionDetailCleanupException error) { throw error; }
            catch (RuntimeException error) {
                held.set(false);
                throw new DataRetentionDetailCleanupException("DETAIL_CLEANUP_LEASE_LOST",
                        "detail cleanup lease validation failed", false, error);
            }
        }
        private DataRetentionDetailCleanupException lost() {
            held.set(false);
            return new DataRetentionDetailCleanupException("DETAIL_CLEANUP_LEASE_LOST", "detail cleanup lease was lost");
        }
        @Override public void close() {
            if (!active.compareAndSet(true, false)) return;
            renewal.cancel(false); release(key, owner);
        }
    }
}
