package com.huanjing.geo.module.mobiledashboard.wechat;

import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatRedisSingleFlightTest {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
    }

    @Test
    void lockContenderWaitsForTheSharedResultInsteadOfFailingFast() {
        when(values.get("cache")).thenReturn(null, null, "shared-value");
        when(values.setIfAbsent(eq("lock"), anyString(), eq(WechatRedisSingleFlight.LOCK_TTL)))
                .thenReturn(false);
        AtomicInteger sleeps = new AtomicInteger();
        WechatRedisSingleFlight singleFlight =
                new WechatRedisSingleFlight(redisTemplate, ignored -> sleeps.incrementAndGet());

        String result = singleFlight.getOrRefresh(
                "cache",
                "lock",
                () -> new WechatRedisSingleFlight.CacheValue("should-not-run", Duration.ofMinutes(5))
        );

        assertThat(result).isEqualTo("shared-value");
        assertThat(sleeps).hasValue(2);
        verify(values, never()).set(eq("cache"), anyString(), any(Duration.class));
    }

    @Test
    void lockOwnerPublishesValueAndReleasesWithAtomicCompareAndDelete() {
        when(values.get("cache")).thenReturn(null, null);
        when(values.setIfAbsent(eq("lock"), anyString(), eq(WechatRedisSingleFlight.LOCK_TTL)))
                .thenReturn(true);
        WechatRedisSingleFlight singleFlight =
                new WechatRedisSingleFlight(redisTemplate, ignored -> {
                });

        String result = singleFlight.getOrRefresh(
                "cache",
                "lock",
                () -> new WechatRedisSingleFlight.CacheValue("fresh-value", Duration.ofMinutes(30))
        );

        assertThat(result).isEqualTo("fresh-value");
        verify(values).set("cache", "fresh-value", Duration.ofMinutes(30));
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("lock")),
                anyString()
        );
        verify(redisTemplate, never()).delete("lock");
    }

    @Test
    void contenderTakesOverWhenThePreviousOwnerExitsWithoutPublishing() {
        when(values.get("cache")).thenReturn(null, null, null);
        when(values.setIfAbsent(eq("lock"), anyString(), eq(WechatRedisSingleFlight.LOCK_TTL)))
                .thenReturn(false, true);
        WechatRedisSingleFlight singleFlight =
                new WechatRedisSingleFlight(redisTemplate, ignored -> {
                });

        String result = singleFlight.getOrRefresh(
                "cache",
                "lock",
                () -> new WechatRedisSingleFlight.CacheValue("recovered-value", Duration.ofMinutes(5))
        );

        assertThat(result).isEqualTo("recovered-value");
        verify(values).set("cache", "recovered-value", Duration.ofMinutes(5));
    }

    @Test
    void interruptedWaitReturnsServiceUnavailableAndRestoresInterruptFlag() {
        when(values.get("cache")).thenReturn(null);
        when(values.setIfAbsent(eq("lock"), anyString(), eq(WechatRedisSingleFlight.LOCK_TTL)))
                .thenReturn(false);
        WechatRedisSingleFlight singleFlight =
                new WechatRedisSingleFlight(redisTemplate, ignored -> {
                    throw new InterruptedException("stop");
                });

        BizException exception = assertThrows(
                BizException.class,
                () -> singleFlight.getOrRefresh(
                        "cache",
                        "lock",
                        () -> new WechatRedisSingleFlight.CacheValue("unused", Duration.ofMinutes(5))
                )
        );

        assertThat(exception.getCode()).isEqualTo(503);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }
}
