package com.huanjing.geo.module.retention.execute;

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
public class DataRetentionExecuteLease {
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>(renewScript(), Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(releaseScript(), Long.class);

    public enum AcquireCode { ACQUIRED, BUSY, UNAVAILABLE }
    public record AcquireResult(AcquireCode code, Lease lease, RuntimeException error) { }

    private final StringRedisTemplate redis;
    private final DataRetentionProperties properties;
    private final TaskScheduler scheduler;

    public DataRetentionExecuteLease(StringRedisTemplate redis,
                                     DataRetentionProperties properties,
                                     TaskScheduler scheduler) {
        this.redis = redis;
        this.properties = properties;
        this.scheduler = scheduler;
    }

    public AcquireResult tryAcquire() {
        String key = properties.getScheduler().getLockKey();
        String owner = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(properties.getScheduler().getLockTtlSeconds());
        try {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, owner, ttl))) {
                return new AcquireResult(AcquireCode.BUSY, null, null);
            }
            AtomicBoolean active = new AtomicBoolean(true);
            AtomicBoolean held = new AtomicBoolean(true);
            Duration interval = Duration.ofMillis(Math.max(1000L, ttl.toMillis() / 3L));
            ScheduledFuture<?> renewal;
            try {
                renewal = scheduler.scheduleAtFixedRate(
                        () -> renewSafely(key, owner, ttl, active, held),
                        Instant.now().plus(interval), interval);
            } catch (RuntimeException error) {
                release(key, owner);
                throw error;
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
        try {
            if (!renew(key, owner, ttl)) held.set(false);
        } catch (RuntimeException error) {
            held.set(false);
            log.warn("Data retention execute lease renewal failed, key={}", key, error);
        }
    }

    private boolean renew(String key, String owner, Duration ttl) {
        Long value = redis.execute(RENEW, List.of(key), owner, Long.toString(ttl.toMillis()));
        return value != null && value == 1L;
    }

    private void release(String key, String owner) {
        try { redis.execute(RELEASE, List.of(key), owner); }
        catch (RuntimeException error) { log.warn("Data retention execute lease release failed, key={}", key, error); }
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
        private final AtomicBoolean held;
        private final ScheduledFuture<?> renewal;

        private Lease(String key, String owner, Duration ttl, AtomicBoolean active,
                      AtomicBoolean held, ScheduledFuture<?> renewal) {
            this.key = key; this.owner = owner; this.ttl = ttl;
            this.active = active; this.held = held; this.renewal = renewal;
        }

        public String owner() { return owner; }
        public boolean isKnownHeld() { return active.get() && held.get(); }

        public void ensureHeld() {
            if (!isKnownHeld()) lost();
            try {
                if (!renew(key, owner, ttl)) lost();
            } catch (RuntimeException error) {
                held.set(false);
                throw new DataRetentionExecutionException(503, "EXECUTE_LEASE_LOST",
                        "Data retention execute lease validation failed", error);
            }
        }

        private void lost() {
            held.set(false);
            throw new DataRetentionExecutionException(503, "EXECUTE_LEASE_LOST",
                    "Data retention execute lease was lost");
        }

        @Override public void close() {
            if (!active.compareAndSet(true, false)) return;
            if (renewal != null) renewal.cancel(false);
            release(key, owner);
        }
    }
}
