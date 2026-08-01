package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchAlertSeverity;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchTaskStateServiceTest {

    private DispatchTaskMapper dispatchTaskMapper;
    private DispatchQueueService dispatchQueueService;
    private DispatchAlertService dispatchAlertService;
    private DispatchPollShardPersistenceService pollShardPersistenceService;
    private DispatchPollAggregationService pollAggregationService;
    private DispatchTaskStateService service;

    @BeforeEach
    void setUp() {
        dispatchTaskMapper = mock(DispatchTaskMapper.class);
        dispatchQueueService = mock(DispatchQueueService.class);
        dispatchAlertService = mock(DispatchAlertService.class);
        pollShardPersistenceService = mock(DispatchPollShardPersistenceService.class);
        pollAggregationService = mock(DispatchPollAggregationService.class);
        service = new DispatchTaskStateService(
                dispatchTaskMapper,
                dispatchQueueService,
                dispatchAlertService,
                pollShardPersistenceService,
                pollAggregationService
        );
    }

    @Test
    void reclaimTimedOutRunningTaskMarksPollShardFailed() {
        DispatchTask task = runningTask(42L, "{\"mode\":\"question-poll-shard\",\"shardId\":88}");
        when(dispatchTaskMapper.claimTimedOutRunningTask(
                eq(42L),
                eq(DispatchTaskStatus.RUNNING.value()),
                eq(DispatchTaskStatus.DEAD_LETTER.value()),
                any(LocalDateTime.class),
                eq("task execution timeout"),
                any(String.class)
        )).thenReturn(1);
        when(pollShardPersistenceService.markShardFailed(88L, "task execution timeout")).thenReturn(99L);

        service.reclaimTimedOutRunningTask(task);

        verify(dispatchQueueService).clearQueueMark(42L);
        verify(dispatchAlertService).createAlert(
                eq(42L),
                eq(7L),
                eq(DispatchAlertSeverity.ERROR),
                eq("Dispatch task timed out while running"),
                eq("task execution timeout"),
                eq(0),
                eq(task.getPayloadJson())
        );
        verify(pollShardPersistenceService).markShardFailed(88L, "task execution timeout");
        verify(pollAggregationService).tryAggregateBatch(99L);
    }

    @Test
    void reclaimTimedOutRunningTaskSkipsShardUpdateWhenPayloadHasNoShardId() {
        DispatchTask task = runningTask(43L, "{\"mode\":\"legacy\"}");
        when(dispatchTaskMapper.claimTimedOutRunningTask(
                eq(43L),
                eq(DispatchTaskStatus.RUNNING.value()),
                eq(DispatchTaskStatus.DEAD_LETTER.value()),
                any(LocalDateTime.class),
                eq("task execution timeout"),
                any(String.class)
        )).thenReturn(1);

        service.reclaimTimedOutRunningTask(task);

        verify(pollShardPersistenceService, never()).markShardFailed(any(), any());
        verify(pollAggregationService, never()).tryAggregateBatch(any());
    }

    @Test
    void markCancelledClearsRetryAndTimeoutState() {
        DispatchTask task = runningTask(44L, "{\"mode\":\"monthly\"}");
        task.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
        task.setTimeoutAt(LocalDateTime.now().plusMinutes(5));
        when(dispatchTaskMapper.selectById(44L)).thenReturn(task);

        service.markCancelled(44L, "retired");

        ArgumentCaptor<DispatchTask> captor = ArgumentCaptor.forClass(DispatchTask.class);
        verify(dispatchTaskMapper).updateById(captor.capture());
        assertEquals(DispatchTaskStatus.CANCELLED.value(), captor.getValue().getStatus());
        assertEquals("retired", captor.getValue().getLastError());
        assertNull(captor.getValue().getNextRetryAt());
        assertNull(captor.getValue().getTimeoutAt());
        verify(dispatchQueueService).clearQueueMark(44L);
    }

    private static DispatchTask runningTask(Long id, String payloadJson) {
        DispatchTask task = new DispatchTask();
        task.setId(id);
        task.setProjectId(7L);
        task.setStatus(DispatchTaskStatus.RUNNING.value());
        task.setRetryCount(0);
        task.setPayloadJson(payloadJson);
        return task;
    }
}
