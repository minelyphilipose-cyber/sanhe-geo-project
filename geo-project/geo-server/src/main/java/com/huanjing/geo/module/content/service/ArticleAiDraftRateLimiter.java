package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.ContentErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleAiDraftRateLimiter {

    private static final int LIMIT_PER_MINUTE = 10;
    private static final DateTimeFormatter WINDOW_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DefaultRedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local current = tonumber(redis.call('GET', KEYS[1]) or '0') " +
                    "local limit = tonumber(ARGV[1]) " +
                    "if current >= limit then return 0 end " +
                    "current = redis.call('INCR', KEYS[1]) " +
                    "if current == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) end " +
                    "if current > limit then return 0 end " +
                    "return 1",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    public void check(Long userId) {
        String minuteWindow = LocalDateTime.now().format(WINDOW_FMT);
        String key = "geo:content:article:ai-draft:user:" + userId + ":" + minuteWindow;
        Long result = stringRedisTemplate.execute(
                LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(LIMIT_PER_MINUTE),
                "90"
        );
        if (result == null || result <= 0) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_RATE_LIMITED,
                    "AI article draft rate limit exceeded");
        }
    }
}
