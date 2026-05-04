package com.huanjing.geo.module.presale.service;

import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PresaleLlmQuestionRateLimiter {

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DefaultRedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local minuteCurrent = tonumber(redis.call('GET', KEYS[1]) or '0') " +
                    "local hourCurrent = tonumber(redis.call('GET', KEYS[2]) or '0') " +
                    "local minuteLimit = tonumber(ARGV[1]) " +
                    "local hourLimit = tonumber(ARGV[2]) " +
                    "if minuteCurrent >= minuteLimit then return 0 end " +
                    "if hourCurrent >= hourLimit then return 0 end " +
                    "minuteCurrent = redis.call('INCR', KEYS[1]) " +
                    "hourCurrent = redis.call('INCR', KEYS[2]) " +
                    "if minuteCurrent == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3])) end " +
                    "if hourCurrent == 1 then redis.call('EXPIRE', KEYS[2], tonumber(ARGV[4])) end " +
                    "if minuteCurrent > minuteLimit then return 0 end " +
                    "if hourCurrent > hourLimit then return 0 end " +
                    "return 1",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    @Value("${presale.llm-question.rate-limit.per-minute:5}")
    private int perMinute;

    @Value("${presale.llm-question.rate-limit.per-hour:30}")
    private int perHour;

    public void acquire(Long userId) {
        String safeUserId = userId == null ? "anonymous" : String.valueOf(userId);
        LocalDateTime now = LocalDateTime.now();
        String minuteKey = "presale:llm-q:rate:" + safeUserId + ":m:" + now.format(MINUTE_FMT);
        String hourKey = "presale:llm-q:rate:" + safeUserId + ":h:" + now.format(HOUR_FMT);
        Long result = redisTemplate.execute(
                LIMIT_SCRIPT,
                List.of(minuteKey, hourKey),
                String.valueOf(Math.max(perMinute, 1)),
                String.valueOf(Math.max(perHour, 1)),
                "120",
                "7200"
        );
        if (result == null || result <= 0) {
            throw new BizException(429, "生成过于频繁，请稍后再试", 429, null);
        }
    }
}
