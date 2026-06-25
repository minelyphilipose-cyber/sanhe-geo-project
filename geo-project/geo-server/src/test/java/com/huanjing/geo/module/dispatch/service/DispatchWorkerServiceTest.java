package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.common.llm.LlmCapacityView;
import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

class DispatchWorkerServiceTest {

    private DispatchWorkerService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
        }
    }

    @Test
    void localAdmissionStopsBeforeClaimWhenInFlightIsFull() throws Exception {
        DispatchQueueService queueService = mock(DispatchQueueService.class);
        DispatchTaskService taskService = mock(DispatchTaskService.class);
        DispatchProperties properties = new DispatchProperties();
        properties.setWorkerPollConcurrency(2);
        properties.setWorkerMaxInFlight(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(queueService.popNextTaskId()).thenReturn(101L);
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(taskService).processTask(101L);
        service = newWorker(queueService, taskService, properties, null, null);
        service.init();

        service.pollQueue();

        assertTrue(started.await(2, TimeUnit.SECONDS));
        verify(queueService, times(1)).popNextTaskId();
        release.countDown();
    }

    @Test
    void emptyQueueReleasesLocalSlot() {
        DispatchQueueService queueService = mock(DispatchQueueService.class);
        DispatchTaskService taskService = mock(DispatchTaskService.class);
        DispatchProperties properties = new DispatchProperties();
        properties.setWorkerPollConcurrency(1);
        properties.setWorkerMaxInFlight(1);
        when(queueService.popNextTaskId()).thenReturn(null);
        service = newWorker(queueService, taskService, properties, null, null);
        service.init();

        service.pollQueue();
        service.pollQueue();

        verify(queueService, times(2)).popNextTaskId();
    }

    @Test
    void claimExceptionReleasesLocalSlot() {
        DispatchQueueService queueService = mock(DispatchQueueService.class);
        DispatchTaskService taskService = mock(DispatchTaskService.class);
        DispatchProperties properties = new DispatchProperties();
        properties.setWorkerPollConcurrency(1);
        properties.setWorkerMaxInFlight(1);
        when(queueService.popNextTaskId())
                .thenThrow(new IllegalStateException("redis unavailable"))
                .thenReturn(null);
        service = newWorker(queueService, taskService, properties, null, null);
        service.init();

        assertThrows(IllegalStateException.class, service::pollQueue);
        service.pollQueue();

        verify(queueService, times(2)).popNextTaskId();
    }

    @Test
    void submitRejectionRequeuesClaimedTaskAndReleasesLocalSlot() throws Exception {
        DispatchQueueService queueService = mock(DispatchQueueService.class);
        DispatchTaskService taskService = mock(DispatchTaskService.class);
        DispatchProperties properties = new DispatchProperties();
        properties.setWorkerPollConcurrency(1);
        properties.setWorkerMaxInFlight(1);
        when(queueService.popNextTaskId()).thenReturn(101L).thenReturn(null);
        service = newWorker(queueService, taskService, properties, null, null);
        service.init();
        ExecutorService executorService = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException("full")).when(executorService).submit(org.mockito.ArgumentMatchers.any(Runnable.class));
        Field executorField = DispatchWorkerService.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        executorField.set(service, executorService);

        assertThrows(RejectedExecutionException.class, service::pollQueue);
        service.pollQueue();

        verify(taskService).enqueueIfNeeded(101L);
        verify(queueService, times(2)).popNextTaskId();
    }

    @Test
    void permitGovernorSkipsClaimWhenGlobalPoolIsBusy() {
        DispatchQueueService queueService = mock(DispatchQueueService.class);
        DispatchTaskService taskService = mock(DispatchTaskService.class);
        DispatchProperties properties = new DispatchProperties();
        properties.setWorkerPermitGovernorEnabled(true);
        properties.setWorkerPermitGovernorBusyRatio(0.9D);
        LlmCapacityView capacityView = mock(LlmCapacityView.class);
        LlmPoolProperties poolProperties = new LlmPoolProperties();
        poolProperties.setEnabled(true);
        poolProperties.setGlobalConcurrency(10);
        when(capacityView.activeGlobalCount()).thenReturn(9L);
        service = newWorker(queueService, taskService, properties, capacityView, poolProperties);
        service.init();

        service.pollQueue();

        verify(queueService, never()).popNextTaskId();
    }

    @SuppressWarnings("unchecked")
    private static DispatchWorkerService newWorker(DispatchQueueService queueService,
                                                   DispatchTaskService taskService,
                                                   DispatchProperties properties,
                                                   LlmCapacityView capacityView,
                                                   LlmPoolProperties poolProperties) {
        ObjectProvider<LlmCapacityView> capacityProvider = mock(ObjectProvider.class);
        ObjectProvider<LlmPoolProperties> poolProvider = mock(ObjectProvider.class);
        when(capacityProvider.getIfAvailable()).thenReturn(capacityView);
        when(poolProvider.getIfAvailable()).thenReturn(poolProperties);
        return new DispatchWorkerService(queueService, taskService, properties, capacityProvider, poolProvider);
    }
}
