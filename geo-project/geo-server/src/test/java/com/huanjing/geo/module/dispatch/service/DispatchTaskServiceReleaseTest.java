package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchTaskServiceReleaseTest {

    private DispatchTaskMapper dispatchTaskMapper;
    private DispatchQueueService dispatchQueueService;
    private CurrentUserService currentUserService;
    private ActivityLogService activityLogService;
    private DispatchTaskService service;

    @BeforeEach
    void setUp() {
        dispatchTaskMapper = mock(DispatchTaskMapper.class);
        dispatchQueueService = mock(DispatchQueueService.class);
        currentUserService = mock(CurrentUserService.class);
        activityLogService = mock(ActivityLogService.class);

        SysUser operator = new SysUser();
        operator.setId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);

        service = new DispatchTaskService(
                dispatchTaskMapper,
                dispatchQueueService,
                mock(DispatchExecutionService.class),
                new DispatchProperties(),
                mock(DispatchTaskStateService.class),
                currentUserService,
                activityLogService,
                mock(DispatchPollShardPersistenceService.class),
                mock(DispatchPollAggregationService.class)
        );
    }

    @Test
    void releaseTaskRewritesIdempotencyKeyAndCancelsTask() {
        DispatchTask task = contentTask(11L, DispatchTaskStatus.RUNNING.value(), "content:official_site:1");
        when(dispatchTaskMapper.selectByIdForUpdate(11L)).thenReturn(task);

        service.releaseTask(11L, "stuck");

        ArgumentCaptor<DispatchTask> captor = ArgumentCaptor.forClass(DispatchTask.class);
        verify(dispatchTaskMapper).updateById(captor.capture());
        DispatchTask updated = captor.getValue();
        assertEquals(DispatchTaskStatus.CANCELLED.value(), updated.getStatus());
        assertEquals("cancelled:content:official_site:1:11", updated.getIdempotencyKey());
        assertEquals("stuck", updated.getLastError());
        verify(dispatchQueueService).clearQueueMark(11L);
        verify(activityLogService).logAction(eq(7L), eq("dispatch.task.release"), eq("dispatch_task"), eq(11L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void releaseTaskRejectsAlreadyCancelledTask() {
        when(dispatchTaskMapper.selectByIdForUpdate(12L))
                .thenReturn(contentTask(12L, DispatchTaskStatus.CANCELLED.value(), "cancelled:key:12"));

        assertThrows(BizException.class, () -> service.releaseTask(12L, "again"));

        verify(dispatchTaskMapper, never()).updateById(org.mockito.ArgumentMatchers.any());
        verify(dispatchQueueService, never()).clearQueueMark(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void releaseTaskRejectsCompletedTask() {
        when(dispatchTaskMapper.selectByIdForUpdate(13L))
                .thenReturn(contentTask(13L, DispatchTaskStatus.COMPLETED.value(), "content:official_site:1"));

        assertThrows(BizException.class, () -> service.releaseTask(13L, "done"));

        verify(dispatchTaskMapper, never()).updateById(org.mockito.ArgumentMatchers.any());
        verify(dispatchQueueService, never()).clearQueueMark(org.mockito.ArgumentMatchers.anyLong());
    }

    private static DispatchTask contentTask(Long id, String status, String idempotencyKey) {
        DispatchTask task = new DispatchTask();
        task.setId(id);
        task.setProjectId(100L);
        task.setTaskType(DispatchTaskType.CONTENT_GENERATION.name());
        task.setStatus(status);
        task.setIdempotencyKey(idempotencyKey);
        task.setTargetChannel("official_site");
        task.setGenerationSlotNo(1);
        return task;
    }
}
