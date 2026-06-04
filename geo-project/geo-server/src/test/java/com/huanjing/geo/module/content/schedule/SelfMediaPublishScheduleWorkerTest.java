package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.service.SelfMediaPublishScheduleService;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaPublishScheduleWorkerTest {

    @Test
    void runOnceProcessesPublishCheckBeforeScheduleExecution() {
        SelfMediaPublishScheduleService service = mock(SelfMediaPublishScheduleService.class);
        SelfMediaPublishScheduleAdapter adapter = mock(SelfMediaPublishScheduleAdapter.class);
        SelfMediaPublishScheduleWorker worker = new SelfMediaPublishScheduleWorker(service, List.of(adapter));
        SelfMediaPublishScheduleVO publishCheck = schedule(10L, "toutiao");
        when(service.claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK), eq(30)))
                .thenReturn(publishCheck);
        when(adapter.supports("toutiao")).thenReturn(true);
        when(adapter.checkPublishResult(publishCheck))
                .thenReturn(PublishCheckResult.published("https://example.test/post/10", "{\"ok\":true}"));

        boolean processed = worker.runOnce();

        assertTrue(processed);
        verify(service).markClaimedPublishedConfirmed(10L, "https://example.test/post/10", "{\"ok\":true}");
        verify(service, never()).claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION), eq(30));
    }

    @Test
    void runOnceMovesSuccessfulScheduleExecutionThroughAllClaimedStates() {
        SelfMediaPublishScheduleService service = mock(SelfMediaPublishScheduleService.class);
        SelfMediaPublishScheduleAdapter adapter = mock(SelfMediaPublishScheduleAdapter.class);
        SelfMediaPublishScheduleWorker worker = new SelfMediaPublishScheduleWorker(service, List.of(adapter));
        SelfMediaPublishScheduleVO schedule = schedule(20L, "toutiao");
        when(service.claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK), eq(30)))
                .thenReturn(null);
        when(service.claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION), eq(30)))
                .thenReturn(schedule);
        when(adapter.supports("toutiao")).thenReturn(true);
        when(adapter.schedule(schedule)).thenReturn(ScheduleExecutionResult.scheduled("platform-20", "{\"ok\":true}"));

        boolean processed = worker.runOnce();

        assertTrue(processed);
        InOrder order = inOrder(service);
        order.verify(service).markClaimedFilledVerified(20L, "{\"ok\":true}");
        order.verify(service).markClaimedScheduling(20L, "{\"ok\":true}");
        order.verify(service).markClaimedScheduled(20L, "platform-20", "{\"ok\":true}");
    }

    @Test
    void runOnceMovesScheduleToManualRequiredWhenAdapterMissing() {
        SelfMediaPublishScheduleService service = mock(SelfMediaPublishScheduleService.class);
        SelfMediaPublishScheduleWorker worker = new SelfMediaPublishScheduleWorker(service, List.of());
        SelfMediaPublishScheduleVO schedule = schedule(30L, "xiaohongshu");
        when(service.claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK), eq(30)))
                .thenReturn(null);
        when(service.claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION), eq(30)))
                .thenReturn(schedule);

        boolean processed = worker.runOnce();

        assertTrue(processed);
        verify(service).markClaimFailed(
                eq(30L),
                eq(SelfMediaPublishScheduleConstants.STATUS_FILLING),
                eq("ADAPTER_NOT_IMPLEMENTED"),
                eq("当前平台尚未接入自动定时发布适配器"),
                eq("{\"adapter\":\"noop\"}"),
                eq(null)
        );
    }

    @Test
    void runBatchStopsWhenNoMoreWork() {
        SelfMediaPublishScheduleService service = mock(SelfMediaPublishScheduleService.class);
        SelfMediaPublishScheduleWorker worker = new SelfMediaPublishScheduleWorker(service, List.of());
        when(service.claimNext(anyString(), eq(30))).thenReturn(null);

        int processed = worker.runBatch(5);

        assertEquals(0, processed);
    }

    private SelfMediaPublishScheduleVO schedule(Long id, String platform) {
        SelfMediaPublishScheduleVO vo = new SelfMediaPublishScheduleVO();
        vo.setId(id);
        vo.setPlatform(platform);
        return vo;
    }
}
