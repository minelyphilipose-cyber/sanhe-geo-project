package com.huanjing.geo.module.system.modeldiagnostic.cleanup;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticCleanupLockStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    void lockUsesNxTtlAndOwnerCheckedRelease() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("key", "owner-a", Duration.ofMinutes(3)))
                .thenReturn(true);
        when(redis.execute(any(DefaultRedisScript.class), eq(List.of("key")), eq("owner-a")))
                .thenReturn(1L);
        ModelDiagnosticCleanupLockStore store = new ModelDiagnosticCleanupLockStore(redis);

        assertTrue(store.tryAcquire("key", "owner-a", Duration.ofMinutes(3)));
        assertTrue(store.release("key", "owner-a"));

        verify(values).setIfAbsent("key", "owner-a", Duration.ofMinutes(3));
        verify(redis).execute(any(DefaultRedisScript.class), eq(List.of("key")), eq("owner-a"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownerMismatchDoesNotReportReleased() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), eq(List.of("key")), eq("owner-b")))
                .thenReturn(0L);
        ModelDiagnosticCleanupLockStore store = new ModelDiagnosticCleanupLockStore(redis);

        assertFalse(store.release("key", "owner-b"));
    }
}
