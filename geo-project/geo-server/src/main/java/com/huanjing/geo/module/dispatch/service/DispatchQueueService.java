package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchQueueService {

    private final StringRedisTemplate redisTemplate;
    private final DispatchProperties dispatchProperties;

    private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT = buildEnqueueScript();
    private static final DefaultRedisScript<String> CLAIM_DUE_SCRIPT = buildClaimDueScript();

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
        return enqueueTask(taskId, priorityLevel, createdAtMillis, createdAtMillis);
    }

    public boolean enqueueTask(Long taskId, int priorityLevel, long createdAtMillis, long availableAtMillis) {
        String dedupeKey = dedupeKey(taskId);
        long score = Math.max(0L, availableAtMillis);
        Long result = redisTemplate.execute(
                ENQUEUE_SCRIPT,
                java.util.List.of(priorityQueueKey(priorityLevel), dedupeKey),
                String.valueOf(score),
                String.valueOf(taskId),
                String.valueOf(TimeUnit.DAYS.toSeconds(2))
        );
        return result != null && result > 0;
    }

    public Long popNextTaskId() {
        String value = redisTemplate.execute(
                CLAIM_DUE_SCRIPT,
                priorityQueueKeys(),
                String.valueOf(System.currentTimeMillis()),
                dedupeKeyPrefix()
        );
        if (value == null || value.isBlank()) {
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
        if (taskId == null) {
            return false;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupeKey(taskId)))) {
            return true;
        }
        String taskIdValue = String.valueOf(taskId);
        for (String key : priorityQueueKeys()) {
            Double score = redisTemplate.opsForZSet().score(key, taskIdValue);
            if (score != null) {
                return true;
            }
        }
        Double legacyScore = redisTemplate.opsForZSet().score(dispatchProperties.getQueueKey(), taskIdValue);
        return legacyScore != null;
    }

    public long queuedTaskCount() {
        long total = 0L;
        for (String key : priorityQueueKeys()) {
            Long size = redisTemplate.opsForZSet().zCard(key);
            total += size == null ? 0L : size;
        }
        Long legacySize = redisTemplate.opsForZSet().zCard(dispatchProperties.getQueueKey());
        total += legacySize == null ? 0L : legacySize;
        return total;
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
        return dedupeKeyPrefix() + taskId;
    }

    private String dedupeKeyPrefix() {
        return "geo:dispatch:queued:";
    }

    String priorityQueueKey(int priorityLevel) {
        // Current production Redis is single-node. If Redis Cluster is introduced,
        // these priority keys must use a shared hash tag to keep the Lua KEYS in one slot.
        return dispatchProperties.getQueueKey() + ":p" + Math.max(priorityLevel, 0);
    }

    private List<String> priorityQueueKeys() {
        return Arrays.stream(DispatchTaskType.values())
                .filter(DispatchTaskType::isQueueTask)
                .map(DispatchTaskType::getPriorityLevel)
                .distinct()
                .sorted()
                .map(this::priorityQueueKey)
                .toList();
    }

    private static DefaultRedisScript<Long> buildEnqueueScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(enqueueScriptText());
        return script;
    }

    static String enqueueScriptText() {
        return "if redis.call('SETNX', KEYS[2], ARGV[2]) == 1 then " +
                "redis.call('EXPIRE', KEYS[2], ARGV[3]); " +
                "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2]); " +
                "return 1; " +
                "end; " +
                "return 0;";
    }

    static String claimDueScriptText() {
        return """
                local now = tonumber(ARGV[1])
                local dedupePrefix = ARGV[2]
                for i = 1, #KEYS do
                  local values = redis.call('ZRANGEBYSCORE', KEYS[i], 0, now, 'LIMIT', 0, 1)
                  if values and #values > 0 then
                    if redis.call('ZREM', KEYS[i], values[1]) == 1 then
                      redis.call('DEL', dedupePrefix .. values[1])
                      return values[1]
                    end
                  end
                end
                return nil
                """;
    }

    private static DefaultRedisScript<String> buildClaimDueScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setResultType(String.class);
        script.setScriptText(claimDueScriptText());
        return script;
    }
}
