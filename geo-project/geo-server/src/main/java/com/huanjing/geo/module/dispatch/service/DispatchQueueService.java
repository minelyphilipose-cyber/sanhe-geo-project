package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchQueueService {

    private final StringRedisTemplate redisTemplate;
    private final DispatchProperties dispatchProperties;

    private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT = buildEnqueueScript();

    public boolean tryAcquireScanLock(String lockValue) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                dispatchProperties.getLockKey(),
                lockValue,
                dispatchProperties.getLockTtlSeconds(),
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(ok);
    }

    public void releaseScanLock(String lockValue) {
        Object current = redisTemplate.opsForValue().get(dispatchProperties.getLockKey());
        if (current != null && lockValue.equals(String.valueOf(current))) {
            redisTemplate.delete(dispatchProperties.getLockKey());
        }
    }

    public boolean enqueueTask(Long taskId, int priorityLevel, long createdAtMillis) {
        String dedupeKey = dedupeKey(taskId);
        long score = score(priorityLevel, createdAtMillis);
        Long result = redisTemplate.execute(
                ENQUEUE_SCRIPT,
                java.util.List.of(dispatchProperties.getQueueKey(), dedupeKey),
                String.valueOf(score),
                String.valueOf(taskId),
                String.valueOf(TimeUnit.DAYS.toSeconds(2))
        );
        return result != null && result > 0;
    }

    public Long popNextTaskId() {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(dispatchProperties.getQueueKey(), 1);
        if (tuples == null || tuples.isEmpty()) {
            return null;
        }
        Object value = tuples.iterator().next().getValue();
        if (value == null) {
            return null;
        }
        try {
            Long taskId = Long.parseLong(String.valueOf(value));
            clearQueueMark(taskId);
            return taskId;
        } catch (NumberFormatException ex) {
            log.warn("Ignore invalid task id in queue: {}", value);
            return null;
        }
    }

    public boolean existsInQueue(Long taskId) {
        Double score = redisTemplate.opsForZSet().score(dispatchProperties.getQueueKey(), String.valueOf(taskId));
        return score != null;
    }

    public void clearQueueMark(Long taskId) {
        redisTemplate.delete(dedupeKey(taskId));
    }

    public boolean isRedisAvailable() {
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) RedisConnection::ping);
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception ex) {
            return false;
        }
    }

    private String dedupeKey(Long taskId) {
        return "geo:dispatch:queued:" + taskId;
    }

    private long score(int priorityLevel, long createdAtMillis) {
        return ((long) priorityLevel * 1_000_000_000_000L) + createdAtMillis;
    }

    private static DefaultRedisScript<Long> buildEnqueueScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "if not redis.call('ZSCORE', KEYS[1], ARGV[2]) then " +
                        "redis.call('DEL', KEYS[2]); " +
                        "end; " +
                        "if redis.call('SETNX', KEYS[2], ARGV[2]) == 1 then " +
                        "redis.call('EXPIRE', KEYS[2], ARGV[3]); " +
                        "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2]); " +
                        "return 1; " +
                        "end; " +
                        "return 0;"
        );
        return script;
    }
}
