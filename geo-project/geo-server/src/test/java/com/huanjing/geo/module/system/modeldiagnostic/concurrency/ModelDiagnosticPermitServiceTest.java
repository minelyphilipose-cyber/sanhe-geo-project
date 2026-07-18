package com.huanjing.geo.module.system.modeldiagnostic.concurrency;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticPermitServiceTest {

    @Test
    void keysShareClusterHashTagAndPermitReleasesOnlyOnce() {
        ModelDiagnosticPermitStore store = mock(ModelDiagnosticPermitStore.class);
        when(store.acquire(anyString(), anyString(), anyString(),
                anyLong(), anyLong(), anyLong())).thenReturn(true);
        when(store.release(anyString(), anyString(), anyString(),
                anyLong(), anyLong())).thenReturn(true);
        ModelDiagnosticPermitService service = new ModelDiagnosticPermitService(store);

        ModelDiagnosticPermit permit = service.tryAcquire(
                7L, LocalDateTime.now().plusMinutes(3));

        assertNotNull(permit);
        assertTrue(ModelDiagnosticPermitService.globalKey().contains("{model-diagnostic}"));
        assertTrue(ModelDiagnosticPermitService.operatorKey(7L).contains("{model-diagnostic}"));
        permit.close();
        permit.close();
        verify(store, times(1)).release(
                eq(ModelDiagnosticPermitService.globalKey()),
                eq(ModelDiagnosticPermitService.operatorKey(7L)),
                anyString(), anyLong(),
                eq(ModelDiagnosticPermitService.LEASE_SAFETY_MILLIS));
    }

    @Test
    void busyAcquisitionStopsAtShortDeadline() {
        ModelDiagnosticPermitStore store = mock(ModelDiagnosticPermitStore.class);
        when(store.acquire(anyString(), anyString(), anyString(),
                anyLong(), anyLong(), anyLong())).thenReturn(false);
        ModelDiagnosticPermitService service = new ModelDiagnosticPermitService(store);

        long started = System.currentTimeMillis();
        ModelDiagnosticPermit permit = service.tryAcquire(
                7L, LocalDateTime.now().plusNanos(80_000_000L));

        assertNull(permit);
        assertTrue(System.currentTimeMillis() - started < 500L);
    }

    @Test
    void redisFailureIsFailClosed() {
        ModelDiagnosticPermitStore store = mock(ModelDiagnosticPermitStore.class);
        when(store.acquire(anyString(), anyString(), anyString(),
                anyLong(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("redis down"));
        ModelDiagnosticPermitService service = new ModelDiagnosticPermitService(store);

        ModelDiagnosticPermitAccessException error = assertThrows(
                ModelDiagnosticPermitAccessException.class,
                () -> service.tryAcquire(7L, LocalDateTime.now().plusMinutes(3)));

        assertEquals(ModelDiagnosticPermitService.UNAVAILABLE_CODE, error.rejectionCode());
    }

    @Test
    void acquisitionDoesNotRetryAfterAnAttemptCrossesDeadline() {
        ModelDiagnosticPermitStore store = mock(ModelDiagnosticPermitStore.class);
        when(store.acquire(anyString(), anyString(), anyString(),
                anyLong(), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    Thread.sleep(80L);
                    return false;
                })
                .thenReturn(true);
        ModelDiagnosticPermitService service = new ModelDiagnosticPermitService(store);

        ModelDiagnosticPermit permit = service.tryAcquire(
                7L, LocalDateTime.now().plusNanos(50_000_000L));

        assertNull(permit);
        verify(store, times(1)).acquire(
                eq(ModelDiagnosticPermitService.globalKey()),
                eq(ModelDiagnosticPermitService.operatorKey(7L)),
                anyString(), anyLong(), anyLong(),
                eq(ModelDiagnosticPermitService.LEASE_SAFETY_MILLIS));
        verify(store, never()).release(anyString(), anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void successfulAcquisitionCrossingDeadlineIsReleasedAndNotReturned() {
        ModelDiagnosticPermitStore store = mock(ModelDiagnosticPermitStore.class);
        when(store.acquire(anyString(), anyString(), anyString(),
                anyLong(), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    Thread.sleep(80L);
                    return true;
                });
        when(store.release(anyString(), anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(true);
        ModelDiagnosticPermitService service = new ModelDiagnosticPermitService(store);

        ModelDiagnosticPermit permit = service.tryAcquire(
                7L, LocalDateTime.now().plusNanos(50_000_000L));

        assertNull(permit);
        verify(store, times(1)).release(
                eq(ModelDiagnosticPermitService.globalKey()),
                eq(ModelDiagnosticPermitService.operatorKey(7L)),
                anyString(), anyLong(),
                eq(ModelDiagnosticPermitService.LEASE_SAFETY_MILLIS));
    }
}
