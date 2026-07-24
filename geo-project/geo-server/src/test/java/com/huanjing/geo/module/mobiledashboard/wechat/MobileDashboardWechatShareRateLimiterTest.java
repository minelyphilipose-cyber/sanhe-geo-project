package com.huanjing.geo.module.mobiledashboard.wechat;

import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobileDashboardWechatShareRateLimiterTest {
    private StringRedisTemplate redisTemplate;
    private MobileDashboardWechatShareRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        MobileDashboardWechatJsSdkProperties properties = new MobileDashboardWechatJsSdkProperties();
        properties.setSignatureRateLimitPerMinute(20);
        properties.setErrorReportRateLimitPerMinute(10);
        limiter = new MobileDashboardWechatShareRateLimiter(redisTemplate, properties);
    }

    @Test
    void allowsRequestWhenRedisScriptReturnsOne() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                eq("20"),
                eq("90")
        )).thenReturn(1L);

        assertDoesNotThrow(() -> limiter.enforceConfig(5L, "203.0.113.9"));
    }

    @Test
    void rejectsRequestAndRecordsMetricAtLimit() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                eq("20"),
                eq("90")
        )).thenReturn(0L);

        BizException exception = assertThrows(
                BizException.class,
                () -> limiter.enforceConfig(5L, "203.0.113.9")
        );

        assertThat(exception.getCode()).isEqualTo(429);
    }

    @Test
    void recordsInfrastructureFailureAndKeepsDashboardAvailable() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                any(List.class),
                eq("20"),
                eq("90")
        )).thenThrow(new IllegalStateException("redis unavailable"));

        assertDoesNotThrow(() -> limiter.enforceConfig(5L, "203.0.113.9"));
    }
}
