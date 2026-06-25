package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DispatchQueueServiceScriptTest {

    @Test
    void claimDueScriptUsesAtomicRangeAndRemove() {
        String script = DispatchQueueService.claimDueScriptText();

        assertThat(script).contains("ZRANGEBYSCORE");
        assertThat(script).contains("ZREM");
        assertThat(script).contains("DEL");
        assertThat(script).contains("dedupePrefix .. values[1]");
        assertThat(script).contains("LIMIT', 0, 1");
        assertThat(script).doesNotContain("ZPOPMIN");
    }

    @Test
    void enqueueScriptUsesIndependentDedupeKey() {
        String script = DispatchQueueService.enqueueScriptText();

        assertThat(script).contains("SETNX");
        assertThat(script).contains("ZADD");
        assertThat(script).doesNotContain("ZSCORE");
    }

    @Test
    void priorityQueueKeyUsesSeparateZsetPerPriority() {
        DispatchProperties properties = new DispatchProperties();
        properties.setQueueKey("geo:dispatch:queue:zset");
        DispatchQueueService service = new DispatchQueueService(mock(StringRedisTemplate.class), properties);

        assertThat(service.priorityQueueKey(3)).isEqualTo("geo:dispatch:queue:zset:p3");
    }
}
