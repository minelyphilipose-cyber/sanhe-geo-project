package com.huanjing.geo.module.system.modeldiagnostic.concurrency;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ModelDiagnosticPermitStore {

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT =
            new DefaultRedisScript<>(acquireScript(), Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(releaseScript(), Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean acquire(String globalKey,
                           String operatorKey,
                           String ownerToken,
                           long nowMillis,
                           long leaseUntilMillis,
                           long safetyMillis) {
        Long acquired = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(globalKey, operatorKey),
                String.valueOf(nowMillis),
                ownerToken,
                String.valueOf(leaseUntilMillis),
                String.valueOf(safetyMillis)
        );
        return acquired != null && acquired == 1L;
    }

    public boolean release(String globalKey,
                           String operatorKey,
                           String ownerToken,
                           long nowMillis,
                           long safetyMillis) {
        Long released = redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(globalKey, operatorKey),
                String.valueOf(nowMillis),
                ownerToken,
                String.valueOf(safetyMillis)
        );
        return released != null && released > 0L;
    }

    static String acquireScript() {
        return """
                local globalKey = KEYS[1]
                local operatorKey = KEYS[2]
                local now = tonumber(ARGV[1])
                local owner = ARGV[2]
                local leaseUntil = tonumber(ARGV[3])
                local safety = tonumber(ARGV[4])

                local serverTime = redis.call('TIME')
                local serverNow = tonumber(serverTime[1]) * 1000
                    + math.floor(tonumber(serverTime[2]) / 1000)
                if serverNow > now then
                    now = serverNow
                end

                if leaseUntil <= now then
                    return 0
                end

                redis.call('ZREMRANGEBYSCORE', globalKey, 0, now)
                redis.call('ZREMRANGEBYSCORE', operatorKey, 0, now)

                local ownsGlobal = redis.call('ZSCORE', globalKey, owner)
                local ownsOperator = redis.call('ZSCORE', operatorKey, owner)
                if ownsGlobal and ownsOperator then
                    return 1
                end
                if ownsGlobal or ownsOperator then
                    redis.call('ZREM', globalKey, owner)
                    redis.call('ZREM', operatorKey, owner)
                end

                if redis.call('ZCARD', globalKey) >= 2 then
                    return 0
                end
                if redis.call('ZCARD', operatorKey) >= 1 then
                    return 0
                end

                redis.call('ZADD', globalKey, leaseUntil, owner)
                redis.call('ZADD', operatorKey, leaseUntil, owner)

                local function refresh(key)
                    local last = redis.call('ZREVRANGE', key, 0, 0, 'WITHSCORES')
                    local ttl = tonumber(last[2]) - now + safety
                    if ttl < safety then
                        ttl = safety
                    end
                    redis.call('PEXPIRE', key, ttl)
                end

                refresh(globalKey)
                refresh(operatorKey)
                return 1
                """;
    }

    static String releaseScript() {
        return """
                local globalKey = KEYS[1]
                local operatorKey = KEYS[2]
                local now = tonumber(ARGV[1])
                local owner = ARGV[2]
                local safety = tonumber(ARGV[3])

                local removedGlobal = redis.call('ZREM', globalKey, owner)
                local removedOperator = redis.call('ZREM', operatorKey, owner)
                redis.call('ZREMRANGEBYSCORE', globalKey, 0, now)
                redis.call('ZREMRANGEBYSCORE', operatorKey, 0, now)

                local function refresh(key)
                    local last = redis.call('ZREVRANGE', key, 0, 0, 'WITHSCORES')
                    if not last or not last[2] then
                        redis.call('DEL', key)
                        return
                    end
                    local ttl = tonumber(last[2]) - now + safety
                    if ttl < safety then
                        ttl = safety
                    end
                    redis.call('PEXPIRE', key, ttl)
                end

                refresh(globalKey)
                refresh(operatorKey)
                return removedGlobal + removedOperator
                """;
    }
}
