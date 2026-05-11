package com.huanjing.geo.common.llm.pool;

import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmExecutionGatewayTest {

    @Test
    void disabledGatewayDoesNotTouchRedis() {
        RedisLlmPermitStore store = mock(RedisLlmPermitStore.class);
        LeaseRenewalService renewalService = mock(LeaseRenewalService.class);
        LlmExecutionGateway gateway = new LlmExecutionGateway(store, disabledProperties(), renewalService, new LlmGatewayMetrics());

        gateway.acquire("article", platform()).close();

        verify(store, never()).acquire(anyString(), anyString(), eq(1), anyLong(), anyLong(), anyLong());
    }

    @Test
    void platformAcquireFailureReleasesGlobalPermit() {
        RedisLlmPermitStore store = mock(RedisLlmPermitStore.class);
        when(store.acquire(eq("geo:llm:permit:global"), anyString(), eq(8), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        when(store.acquire(eq("geo:llm:permit:platform:openai"), anyString(), eq(2), anyLong(), anyLong(), anyLong()))
                .thenReturn(false);
        LeaseRenewalService renewalService = mock(LeaseRenewalService.class);
        LlmExecutionGateway gateway = new LlmExecutionGateway(store, enabledProperties(), renewalService, new LlmGatewayMetrics());

        assertThrows(LlmPermitUnavailableException.class, () -> gateway.acquire("article", platform()));

        verify(store).release(eq("geo:llm:permit:global"), anyString(), anyLong(), eq(60_000L));
    }

    private static LlmPoolProperties enabledProperties() {
        LlmPoolProperties properties = disabledProperties();
        properties.setEnabled(true);
        return properties;
    }

    private static LlmPoolProperties disabledProperties() {
        LlmPoolProperties properties = new LlmPoolProperties();
        properties.setGlobalConcurrency(8);
        properties.setPermitKeyPrefix("geo:llm:permit");
        properties.setLeaseMs(600_000L);
        properties.setLeaseSafetyMs(60_000L);
        return properties;
    }

    private static AiPlatformConfig platform() {
        AiPlatformConfig config = new AiPlatformConfig();
        config.setPlatformCode("openai");
        config.setPlatformName("OpenAI");
        config.setConcurrencyLimit(2);
        return config;
    }
}
