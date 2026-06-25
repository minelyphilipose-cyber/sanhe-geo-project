package com.huanjing.geo.common.llm.pool;

import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmExecutionGatewayTest {
    private RedisLlmPermitStore store;
    private LeaseRenewalService renewalService;

    @BeforeEach
    void setUp() {
        store = mock(RedisLlmPermitStore.class);
        renewalService = mock(LeaseRenewalService.class);
    }

    @Test
    void disabledGatewayDoesNotTouchRedis() {
        LlmExecutionGateway gateway = new LlmExecutionGateway(store, disabledProperties(), renewalService, new LlmGatewayMetrics());

        gateway.acquire("article", platform()).close();

        verify(store, never()).acquire(anyString(), anyString(), eq(1), anyLong(), anyLong(), anyLong());
    }

    @Test
    void platformAcquireFailureReleasesFeatureAndGlobalPermits() {
        when(store.acquire(eq("geo:llm:permit:global"), anyString(), eq(8), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        when(store.acquire(eq("geo:llm:permit:feature:article"), anyString(), eq(4), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        when(store.acquire(eq("geo:llm:permit:platform:openai"), anyString(), eq(2), anyLong(), anyLong(), anyLong()))
                .thenReturn(false);
        LlmExecutionGateway gateway = new LlmExecutionGateway(store, enabledProperties(), renewalService, new LlmGatewayMetrics());

        LlmPermitUnavailableException ex = assertThrows(LlmPermitUnavailableException.class, () -> gateway.acquire("article", platform()));

        assertEquals(LlmPermitScope.PLATFORM, ex.getScope());
        InOrder inOrder = inOrder(store);
        inOrder.verify(store).release(eq("geo:llm:permit:feature:article"), anyString(), anyLong(), eq(60_000L));
        inOrder.verify(store).release(eq("geo:llm:permit:global"), anyString(), anyLong(), eq(60_000L));
    }

    @Test
    void featureAcquireFailureReleasesGlobalAndSkipsPlatformPermit() {
        when(store.acquire(eq("geo:llm:permit:global"), anyString(), eq(8), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        when(store.acquire(eq("geo:llm:permit:feature:article"), anyString(), eq(4), anyLong(), anyLong(), anyLong()))
                .thenReturn(false);
        LlmExecutionGateway gateway = new LlmExecutionGateway(store, enabledProperties(), renewalService, new LlmGatewayMetrics());

        LlmPermitUnavailableException ex = assertThrows(LlmPermitUnavailableException.class, () -> gateway.acquire("article", platform()));

        assertEquals(LlmPermitScope.FEATURE, ex.getScope());
        assertEquals("article", ex.getPlatformCode());
        verify(store).release(eq("geo:llm:permit:global"), anyString(), anyLong(), eq(60_000L));
        verify(store, never()).acquire(eq("geo:llm:permit:platform:openai"), anyString(), eq(2), anyLong(), anyLong(), anyLong());
    }

    @Test
    void successfulAcquireRegistersAndReleasesGlobalFeatureAndPlatformTokens() {
        when(store.acquire(eq("geo:llm:permit:global"), anyString(), eq(8), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        when(store.acquire(eq("geo:llm:permit:feature:article"), anyString(), eq(4), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        when(store.acquire(eq("geo:llm:permit:platform:openai"), anyString(), eq(2), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        LlmExecutionGateway gateway = new LlmExecutionGateway(store, enabledProperties(), renewalService, new LlmGatewayMetrics());

        LlmExecutionPermit permit = gateway.acquire("article", platform());
        permit.close();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LlmPermitToken>> tokenCaptor = ArgumentCaptor.forClass(List.class);
        verify(renewalService).register(tokenCaptor.capture());
        assertEquals(List.of("GLOBAL", "FEATURE", "PLATFORM"),
                tokenCaptor.getValue().stream().map(LlmPermitToken::scope).toList());

        InOrder inOrder = inOrder(store);
        inOrder.verify(store).release(eq("geo:llm:permit:platform:openai"), anyString(), anyLong(), eq(60_000L));
        inOrder.verify(store).release(eq("geo:llm:permit:feature:article"), anyString(), anyLong(), eq(60_000L));
        inOrder.verify(store).release(eq("geo:llm:permit:global"), anyString(), anyLong(), eq(60_000L));
    }

    @Test
    void acquireBlockingRetriesPermitBusyWithinTimeout() {
        when(store.acquire(eq("geo:llm:permit:global"), anyString(), eq(8), anyLong(), anyLong(), anyLong()))
                .thenReturn(false, true);
        when(store.acquire(eq("geo:llm:permit:feature:article"), anyString(), eq(4), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        when(store.acquire(eq("geo:llm:permit:platform:openai"), anyString(), eq(2), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        LlmPoolProperties properties = enabledProperties();
        properties.setPermitWaitTimeoutMs(200L);
        properties.setPermitRetryIntervalMs(10L);
        LlmExecutionGateway gateway = new LlmExecutionGateway(store, properties, renewalService, new LlmGatewayMetrics());

        assertDoesNotThrow(() -> gateway.acquireBlocking("article", platform()).close());

        verify(store, times(2)).acquire(eq("geo:llm:permit:global"), anyString(), eq(8), anyLong(), anyLong(), anyLong());
    }

    @Test
    void acquireBlockingFailFastFlagDoesNotRetryOrRegisterWaiter() {
        when(store.acquire(eq("geo:llm:permit:global"), anyString(), eq(8), anyLong(), anyLong(), anyLong()))
                .thenReturn(false, true);
        LlmPoolProperties properties = enabledProperties();
        properties.setBlockingAcquireFailFastEnabled(true);
        properties.setBlockingAcquireFailFastFeatures(Set.of("article"));
        properties.setPermitWaitTimeoutMs(200L);
        properties.setPermitRetryIntervalMs(10L);
        LlmExecutionGateway gateway = new LlmExecutionGateway(store, properties, renewalService, new LlmGatewayMetrics());

        LlmPermitUnavailableException ex = assertThrows(
                LlmPermitUnavailableException.class,
                () -> gateway.acquireBlocking("article", platform())
        );

        assertEquals(LlmPermitScope.GLOBAL, ex.getScope());
        assertEquals(0L, gateway.activeWaiterCount());
        verify(store, times(1)).acquire(eq("geo:llm:permit:global"), anyString(), eq(8), anyLong(), anyLong(), anyLong());
    }

    @Test
    void acquireBlockingFailFastMasterSwitchWithoutFeatureKeepsBlockingBehavior() {
        when(store.acquire(eq("geo:llm:permit:global"), anyString(), eq(8), anyLong(), anyLong(), anyLong()))
                .thenReturn(false, true);
        when(store.acquire(eq("geo:llm:permit:feature:article"), anyString(), eq(4), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        when(store.acquire(eq("geo:llm:permit:platform:openai"), anyString(), eq(2), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        LlmPoolProperties properties = enabledProperties();
        properties.setBlockingAcquireFailFastEnabled(true);
        properties.setPermitWaitTimeoutMs(200L);
        properties.setPermitRetryIntervalMs(10L);
        LlmExecutionGateway gateway = new LlmExecutionGateway(store, properties, renewalService, new LlmGatewayMetrics());

        assertDoesNotThrow(() -> gateway.acquireBlocking("article", platform()).close());

        assertEquals(0L, gateway.activeWaiterCount());
        verify(store, times(2)).acquire(eq("geo:llm:permit:global"), anyString(), eq(8), anyLong(), anyLong(), anyLong());
    }

    @Test
    void activeFeatureCountsUsesConfiguredFeatureKeys() {
        LlmPoolProperties properties = enabledProperties();
        properties.setFeatureConcurrency(Map.of("monitoring", 8, "article", 4));
        when(store.activeCount("geo:llm:permit:feature:monitoring")).thenReturn(3L);
        when(store.activeCount("geo:llm:permit:feature:article")).thenReturn(1L);
        LlmExecutionGateway gateway = new LlmExecutionGateway(store, properties, renewalService, new LlmGatewayMetrics());

        Map<String, Long> counts = gateway.activeFeatureCounts();

        assertEquals(3L, counts.get("monitoring"));
        assertEquals(1L, counts.get("article"));
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
