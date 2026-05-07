package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.ContentErrorCodes;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleAiDraftRateLimiterTest {

    @Test
    void eleventhRequestIsRateLimited() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(String.class), any(String.class)))
                .thenReturn(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L);

        ArticleAiDraftRateLimiter limiter = new ArticleAiDraftRateLimiter(redis);
        for (int i = 0; i < 10; i++) {
            limiter.check(7L);
        }

        BizException ex = assertThrows(BizException.class, () -> limiter.check(7L));
        assertEquals(ContentErrorCodes.ARTICLE_AI_DRAFT_RATE_LIMITED, ex.getCode());
    }
}
