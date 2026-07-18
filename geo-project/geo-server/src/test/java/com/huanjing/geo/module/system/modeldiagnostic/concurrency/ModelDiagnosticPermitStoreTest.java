package com.huanjing.geo.module.system.modeldiagnostic.concurrency;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticPermitStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    void acquireUsesOneAtomicScriptForGlobalAndOperatorKeys() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), any(List.class),
                any(), any(), any(), any())).thenReturn(1L);
        ModelDiagnosticPermitStore store = new ModelDiagnosticPermitStore(redis);

        assertTrue(store.acquire("global", "operator", "owner", 100L, 1_000L, 30L));

        verify(redis).execute(any(DefaultRedisScript.class),
                eq(List.of("global", "operator")),
                eq("100"), eq("owner"), eq("1000"), eq("30"));
    }

    @Test
    void scriptsFreezeLimitsOwnerReleaseAndTtl() {
        String acquire = ModelDiagnosticPermitStore.acquireScript();
        String release = ModelDiagnosticPermitStore.releaseScript();

        assertTrue(acquire.contains("ZCARD', globalKey) >= 2"));
        assertTrue(acquire.contains("ZCARD', operatorKey) >= 1"));
        assertTrue(acquire.contains("redis.call('TIME')"));
        assertTrue(acquire.contains("if serverNow > now"));
        assertTrue(acquire.contains("if leaseUntil <= now"));
        assertTrue(acquire.contains("ZADD', globalKey, leaseUntil, owner"));
        assertTrue(acquire.contains("ZADD', operatorKey, leaseUntil, owner"));
        assertTrue(acquire.contains("ZREVRANGE', key, 0, 0, 'WITHSCORES'"));
        assertTrue(acquire.contains("refresh(globalKey)"));
        assertTrue(acquire.contains("refresh(operatorKey)"));
        assertTrue(release.contains("ZREM', globalKey, owner"));
        assertTrue(release.contains("ZREM', operatorKey, owner"));
        assertFalse(release.contains("DEL', globalKey, owner"));
    }
}
