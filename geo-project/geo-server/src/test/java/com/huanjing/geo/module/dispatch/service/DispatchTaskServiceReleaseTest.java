package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.capacity.LlmCapacityFailure;
import com.huanjing.geo.common.llm.capacity.LlmCapacityFailureClassifier;
import com.huanjing.geo.common.llm.measurement.LlmErrorCategory;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.apache.ibatis.annotations.Delete;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchTaskServiceReleaseTest {

    private DispatchTaskMapper dispatchTaskMapper;
    private DispatchQueueService dispatchQueueService;
    private DispatchExecutionService dispatchExecutionService;
    private DispatchTaskStateService dispatchTaskStateService;
    private CurrentUserService currentUserService;
    private ActivityLogService activityLogService;
    private DispatchAlertService dispatchAlertService;
    private DispatchTaskService service;

    @BeforeEach
    void setUp() {
        dispatchTaskMapper = mock(DispatchTaskMapper.class);
        dispatchQueueService = mock(DispatchQueueService.class);
        dispatchExecutionService = mock(DispatchExecutionService.class);
        dispatchTaskStateService = mock(DispatchTaskStateService.class);
        currentUserService = mock(CurrentUserService.class);
        activityLogService = mock(ActivityLogService.class);
        dispatchAlertService = mock(DispatchAlertService.class);

        SysUser operator = new SysUser();
        operator.setId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);

        service = newService(new DispatchProperties());
    }

    @Test
    void processTaskDoesNotDeadLetterPendingTaskBeforeItStarts() {
        DispatchTask task = contentTask(21L, DispatchTaskStatus.PENDING.value(), "content:official_site:2");
        task.setTimeoutAt(LocalDateTime.now().minusMinutes(1));
        when(dispatchTaskMapper.selectById(21L)).thenReturn(task);
        when(dispatchTaskStateService.markRunning(eq(21L), anyInt())).thenReturn(task);

        service.processTask(21L);

        verify(dispatchTaskStateService).markRunning(eq(21L), anyInt());
        verify(dispatchExecutionService).execute(task);
        verify(dispatchTaskStateService).markCompleted(21L);
        verify(dispatchQueueService, never()).clearQueueMark(21L);
    }

    @Test
    void retryAfterDelayIsNotClampedByFallbackMax() throws Exception {
        DispatchProperties properties = new DispatchProperties();
        properties.setResourceBusyRetryAfterEnabled(true);
        properties.setResourceBusyRetryMaxSeconds(900);
        DispatchTaskService retryService = newService(properties);
        Method method = DispatchTaskService.class.getDeclaredMethod(
                "resolveResourceBusyRetryDelaySeconds",
                int.class,
                LlmCapacityFailure.class
        );
        method.setAccessible(true);

        int delaySeconds = (Integer) method.invoke(
                retryService,
                1,
                new LlmCapacityFailure(LlmErrorCategory.PLATFORM_429, 3_600_000L, "rate_limit", "test")
        );

        assertEquals(3600, delaySeconds);
    }

    @Test
    void cleanupHistoryUsesReferenceSafeDelete() {
        DispatchProperties properties = new DispatchProperties();
        properties.setTaskRetentionDays(90);
        service = newService(properties);
        when(dispatchTaskMapper.deleteUnreferencedTerminalBefore(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(4);

        LocalDateTime earliestExpectedDeadline = LocalDateTime.now().minusDays(90).minusSeconds(1);
        int deleted = service.cleanupHistory();
        LocalDateTime latestExpectedDeadline = LocalDateTime.now().minusDays(90).plusSeconds(1);

        assertEquals(4, deleted);
        ArgumentCaptor<LocalDateTime> deadline = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(dispatchTaskMapper).deleteUnreferencedTerminalBefore(deadline.capture());
        assertTrue(!deadline.getValue().isBefore(earliestExpectedDeadline));
        assertTrue(!deadline.getValue().isAfter(latestExpectedDeadline));
    }

    @Test
    void cleanupSqlPreservesTasksReferencedByBusinessEvidence() throws Exception {
        Method method = DispatchTaskMapper.class.getMethod(
                "deleteUnreferencedTerminalBefore",
                LocalDateTime.class
        );
        Delete annotation = method.getAnnotation(Delete.class);
        String sql = String.join(" ", annotation.value());

        assertTrue(sql.contains("article_batch.dispatch_task_id = dispatch_task.id"));
        assertTrue(sql.contains("presale_diagnosis_batches.dispatch_task_id = dispatch_task.id"));
        assertTrue(sql.contains("dispatch_alert.task_id = dispatch_task.id"));
        assertTrue(sql.contains("'completed', 'failed', 'dead_letter', 'cancelled'"));
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

    @Test
    void questionPollStaggerUsesSeparateTimelinePerPlatform() {
        DispatchProperties properties = new DispatchProperties();
        properties.getStagger().setEnabled(true);
        properties.getStagger().setTaskTypes("BI_DAILY_POLL");
        properties.getStagger().setWindowMinutes(10);
        properties.getStagger().setMaxDelayMinutes(10);
        properties.getStagger().setJitterSeconds(0);
        properties.getStagger().setCapJitterSeconds(0);
        DispatchTaskService staggerService = newService(properties);
        DispatchTask qwenOne = pollTask(101L, "qwen");
        DispatchTask qwenTwo = pollTask(102L, "qwen");
        DispatchTask deepseekOne = pollTask(201L, "deepseek");
        when(dispatchTaskMapper.selectById(101L)).thenReturn(qwenOne);
        when(dispatchTaskMapper.selectById(102L)).thenReturn(qwenTwo);
        when(dispatchTaskMapper.selectById(201L)).thenReturn(deepseekOne);

        staggerService.enqueueQuestionPollShardTasksWithStagger(List.of(qwenOne, qwenTwo, deepseekOne));

        ArgumentCaptor<Long> taskIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> availableAtCaptor = ArgumentCaptor.forClass(Long.class);
        verify(dispatchQueueService, org.mockito.Mockito.times(3)).enqueueTask(
                taskIdCaptor.capture(),
                eq(DispatchTaskType.BI_DAILY_POLL.getPriorityLevel()),
                anyLong(),
                availableAtCaptor.capture()
        );
        List<Long> taskIds = taskIdCaptor.getAllValues();
        List<Long> availableAt = availableAtCaptor.getAllValues();
        int qwenOneIndex = taskIds.indexOf(101L);
        int qwenTwoIndex = taskIds.indexOf(102L);
        int deepseekIndex = taskIds.indexOf(201L);

        assertTrue(availableAt.get(qwenTwoIndex) - availableAt.get(qwenOneIndex) >= 250_000L);
        assertTrue(Math.abs(availableAt.get(deepseekIndex) - availableAt.get(qwenOneIndex)) < 30_000L);
    }

    @Test
    void questionPollStaggerUsesPlatformOverrideWhenConfigured() {
        DispatchProperties properties = new DispatchProperties();
        properties.getStagger().setEnabled(true);
        properties.getStagger().setTaskTypes("BI_DAILY_POLL");
        properties.getStagger().setWindowMinutes(10);
        properties.getStagger().setMaxDelayMinutes(10);
        properties.getStagger().setJitterSeconds(0);
        properties.getStagger().setCapJitterSeconds(0);
        DispatchProperties.PlatformOverride qwenOverride = new DispatchProperties.PlatformOverride();
        qwenOverride.setWindowMinutes(20);
        qwenOverride.setMaxDelayMinutes(20);
        qwenOverride.setJitterSeconds(0);
        qwenOverride.setCapJitterSeconds(0);
        properties.getStagger().getPlatforms().put("qwen", qwenOverride);

        DispatchTaskService staggerService = newService(properties);
        DispatchTask qwenOne = pollTask(301L, "qwen");
        DispatchTask qwenTwo = pollTask(302L, "qwen");
        DispatchTask deepseekOne = pollTask(401L, "deepseek");
        DispatchTask deepseekTwo = pollTask(402L, "deepseek");
        when(dispatchTaskMapper.selectById(301L)).thenReturn(qwenOne);
        when(dispatchTaskMapper.selectById(302L)).thenReturn(qwenTwo);
        when(dispatchTaskMapper.selectById(401L)).thenReturn(deepseekOne);
        when(dispatchTaskMapper.selectById(402L)).thenReturn(deepseekTwo);

        staggerService.enqueueQuestionPollShardTasksWithStagger(List.of(qwenOne, qwenTwo, deepseekOne, deepseekTwo));

        ArgumentCaptor<Long> taskIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> availableAtCaptor = ArgumentCaptor.forClass(Long.class);
        verify(dispatchQueueService, org.mockito.Mockito.times(4)).enqueueTask(
                taskIdCaptor.capture(),
                eq(DispatchTaskType.BI_DAILY_POLL.getPriorityLevel()),
                anyLong(),
                availableAtCaptor.capture()
        );
        List<Long> taskIds = taskIdCaptor.getAllValues();
        List<Long> availableAt = availableAtCaptor.getAllValues();
        long qwenGap = availableAt.get(taskIds.indexOf(302L)) - availableAt.get(taskIds.indexOf(301L));
        long deepseekGap = availableAt.get(taskIds.indexOf(402L)) - availableAt.get(taskIds.indexOf(401L));

        assertTrue(qwenGap >= 550_000L);
        assertTrue(deepseekGap >= 250_000L && deepseekGap < 550_000L);
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

    private static DispatchTask pollTask(Long id, String platformCode) {
        DispatchTask task = new DispatchTask();
        task.setId(id);
        task.setProjectId(100L);
        task.setTaskType(DispatchTaskType.BI_DAILY_POLL.name());
        task.setStatus(DispatchTaskStatus.PENDING.value());
        task.setPriorityLevel(DispatchTaskType.BI_DAILY_POLL.getPriorityLevel());
        task.setPlatformCode(platformCode);
        return task;
    }

    private DispatchTaskService newService(DispatchProperties properties) {
        return new DispatchTaskService(
                dispatchTaskMapper,
                dispatchQueueService,
                dispatchExecutionService,
                properties,
                dispatchTaskStateService,
                currentUserService,
                activityLogService,
                mock(DispatchPollShardPersistenceService.class),
                mock(DispatchPollAggregationService.class),
                new LlmCapacityFailureClassifier(),
                dispatchAlertService
        );
    }
}
