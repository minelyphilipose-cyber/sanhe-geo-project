package com.huanjing.geo.common.llm.pool;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisLlmPermitStore {
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(acquireScript(), Long.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(renewScript(), Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(releaseScript(), Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean acquire(String key, String member, int limit, long nowMillis, long leaseUntilMillis, long safetyMillis) {
        Long result = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(key),
                String.valueOf(nowMillis),
                String.valueOf(Math.max(limit, 1)),
                member,
                String.valueOf(leaseUntilMillis),
                String.valueOf(Math.max(safetyMillis, 1L))
        );
        return result != null && result > 0;
    }

    public boolean renew(String key, String member, long nowMillis, long newLeaseUntilMillis, long safetyMillis) {
        Long result = redisTemplate.execute(
                RENEW_SCRIPT,
                List.of(key),
                String.valueOf(nowMillis),
                member,
                String.valueOf(newLeaseUntilMillis),
                String.valueOf(Math.max(safetyMillis, 1L))
        );
        return result != null && result > 0;
    }

    public boolean release(String key, String member, long nowMillis, long safetyMillis) {
        Long result = redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(key),
                String.valueOf(nowMillis),
                member,
                String.valueOf(Math.max(safetyMillis, 1L))
        );
        return result != null && result > 0;
    }

    public Long activeCount(String key) {
        return redisTemplate.opsForZSet().zCard(key);
    }

    static String acquireScript() {
        return """
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local limit = tonumber(ARGV[2])
                local member = ARGV[3]
                local leaseUntil = tonumber(ARGV[4])
                local safety = tonumber(ARGV[5])

                redis.call('ZREMRANGEBYSCORE', key, 0, now)

                -- Defensive idempotency for a retried acquire with the same token.
                if redis.call('ZSCORE', key, member) then
                  return 1
                end

                local active = redis.call('ZCARD', key)
                if active >= limit then
                  return 0
                end

                redis.call('ZADD', key, leaseUntil, member)

                local maxRows = redis.call('ZREVRANGE', key, 0, 0, 'WITHSCORES')
                local maxScore = leaseUntil
                if maxRows and maxRows[2] then
                  maxScore = tonumber(maxRows[2])
                end

                local ttl = maxScore - now + safety
                if ttl < safety then
                  ttl = safety
                end
                redis.call('PEXPIRE', key, ttl)

                return 1
                """;
    }

    static String renewScript() {
        return """
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local member = ARGV[2]
                local leaseUntil = tonumber(ARGV[3])
                local safety = tonumber(ARGV[4])

                redis.call('ZREMRANGEBYSCORE', key, 0, now)

                if not redis.call('ZSCORE', key, member) then
                  return 0
                end

                redis.call('ZADD', key, leaseUntil, member)

                local maxRows = redis.call('ZREVRANGE', key, 0, 0, 'WITHSCORES')
                local maxScore = leaseUntil
                if maxRows and maxRows[2] then
                  maxScore = tonumber(maxRows[2])
                end

                local ttl = maxScore - now + safety
                if ttl < safety then
                  ttl = safety
                end
                redis.call('PEXPIRE', key, ttl)

                return 1
                """;
    }

    static String releaseScript() {
        return """
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local member = ARGV[2]
                local safety = tonumber(ARGV[3])

                local removed = redis.call('ZREM', key, member)
                redis.call('ZREMRANGEBYSCORE', key, 0, now)

                local active = redis.call('ZCARD', key)
                if active == 0 then
                  redis.call('DEL', key)
                  return removed
                end

                local maxRows = redis.call('ZREVRANGE', key, 0, 0, 'WITHSCORES')
                if maxRows and maxRows[2] then
                  local ttl = tonumber(maxRows[2]) - now + safety
                  if ttl < safety then
                    ttl = safety
                  end
                  redis.call('PEXPIRE', key, ttl)
                end

                return removed
                """;
    }
}
