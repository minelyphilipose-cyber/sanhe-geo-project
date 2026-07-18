package com.huanjing.geo.module.system.modeldiagnostic.cleanup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticCleanupLockServiceTest {

    @Test
    void redisFailureFailsClosedAndSkipsCleanup() {
        ModelDiagnosticCleanupLockStore store = mock(ModelDiagnosticCleanupLockStore.class);
        when(store.tryAcquire(
                ModelDiagnosticCleanupLockService.LOCK_KEY, "owner",
                ModelDiagnosticCleanupLockService.LOCK_TTL))
                .thenThrow(new IllegalStateException("redis down"));
        ModelDiagnosticCleanupLockService service =
                new ModelDiagnosticCleanupLockService(store);

        assertFalse(service.tryAcquire("owner"));
    }

    @Test
    void lockDelegatesWithFrozenKeyTtlAndOwnerToken() {
        ModelDiagnosticCleanupLockStore store = mock(ModelDiagnosticCleanupLockStore.class);
        when(store.tryAcquire(
                ModelDiagnosticCleanupLockService.LOCK_KEY, "owner",
                ModelDiagnosticCleanupLockService.LOCK_TTL)).thenReturn(true);
        ModelDiagnosticCleanupLockService service =
                new ModelDiagnosticCleanupLockService(store);

        assertTrue(service.tryAcquire("owner"));
        service.release("owner");

        verify(store).release(ModelDiagnosticCleanupLockService.LOCK_KEY, "owner");
    }
}
