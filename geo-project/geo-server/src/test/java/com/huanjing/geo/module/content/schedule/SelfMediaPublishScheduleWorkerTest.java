package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.service.SelfMediaPublishScheduleService;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SelfMediaPublishScheduleWorkerTest {

    @Test
    void workerJobIsEnabledByDefault() {
        ConditionalOnProperty condition = SelfMediaPublishScheduleWorkerJob.class.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(condition);
        assertEquals("geo.self-media-schedule.worker", condition.prefix());
        assertEquals("enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
        assertTrue(condition.matchIfMissing());
    }

    @Test
    void runOnceProcessesPublishCheckBeforeScheduleExecution() {
        SelfMediaPublishScheduleService service = mock(SelfMediaPublishScheduleService.class);
        SelfMediaPlatformScheduleAdapterRouter router = mock(SelfMediaPlatformScheduleAdapterRouter.class);
        SelfMediaPublishScheduleAdapter adapter = mock(SelfMediaPublishScheduleAdapter.class);
        SelfMediaPublishScheduleWorker worker = new SelfMediaPublishScheduleWorker(service, router, List.of(adapter));
        Set<String> apiPlatforms = Set.of("douyin");
        SelfMediaPublishScheduleVO publishCheck = schedule(10L, "douyin");
        when(router.platformsByChannel(SelfMediaPlatformPublishChannel.OFFICIAL_API)).thenReturn(apiPlatforms);
        when(service.claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK), eq(30), eq(apiPlatforms)))
                .thenReturn(publishCheck);
        when(adapter.supports("douyin")).thenReturn(true);
        when(adapter.checkPublishResult(publishCheck))
                .thenReturn(PublishCheckResult.published("https://example.test/post/10", "{\"ok\":true}"));

        boolean processed = worker.runOnce();

        assertTrue(processed);
        verify(service).markClaimedPublishedConfirmed(10L, "https://example.test/post/10", "{\"ok\":true}");
        verify(service, never()).claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION), eq(30), eq(apiPlatforms));
    }

    @Test
    void runOnceMovesSuccessfulScheduleExecutionThroughAllClaimedStates() {
        SelfMediaPublishScheduleService service = mock(SelfMediaPublishScheduleService.class);
        SelfMediaPlatformScheduleAdapterRouter router = mock(SelfMediaPlatformScheduleAdapterRouter.class);
        SelfMediaPublishScheduleAdapter adapter = mock(SelfMediaPublishScheduleAdapter.class);
        SelfMediaPublishScheduleWorker worker = new SelfMediaPublishScheduleWorker(service, router, List.of(adapter));
        Set<String> apiPlatforms = Set.of("douyin");
        SelfMediaPublishScheduleVO schedule = schedule(20L, "douyin");
        when(router.platformsByChannel(SelfMediaPlatformPublishChannel.OFFICIAL_API)).thenReturn(apiPlatforms);
        when(service.claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK), eq(30), eq(apiPlatforms)))
                .thenReturn(null);
        when(service.claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION), eq(30), eq(apiPlatforms)))
                .thenReturn(schedule);
        when(adapter.supports("douyin")).thenReturn(true);
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
        SelfMediaPlatformScheduleAdapterRouter router = mock(SelfMediaPlatformScheduleAdapterRouter.class);
        SelfMediaPublishScheduleWorker worker = new SelfMediaPublishScheduleWorker(service, router, List.of());
        Set<String> apiPlatforms = Set.of("douyin");
        SelfMediaPublishScheduleVO schedule = schedule(30L, "douyin");
        when(router.platformsByChannel(SelfMediaPlatformPublishChannel.OFFICIAL_API)).thenReturn(apiPlatforms);
        when(service.claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK), eq(30), eq(apiPlatforms)))
                .thenReturn(null);
        when(service.claimNext(eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION), eq(30), eq(apiPlatforms)))
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
        SelfMediaPlatformScheduleAdapterRouter router = mock(SelfMediaPlatformScheduleAdapterRouter.class);
        SelfMediaPublishScheduleWorker worker = new SelfMediaPublishScheduleWorker(service, router, List.of());
        Set<String> apiPlatforms = Set.of("douyin");
        when(router.platformsByChannel(SelfMediaPlatformPublishChannel.OFFICIAL_API)).thenReturn(apiPlatforms);
        when(service.claimNext(anyString(), eq(30), eq(apiPlatforms))).thenReturn(null);

        int processed = worker.runBatch(5);

        assertEquals(0, processed);
    }

    @Test
    void runOnceSkipsWhenNoOfficialApiExecutorPlatformExists() {
        SelfMediaPublishScheduleService service = mock(SelfMediaPublishScheduleService.class);
        SelfMediaPlatformScheduleAdapterRouter router = mock(SelfMediaPlatformScheduleAdapterRouter.class);
        SelfMediaPublishScheduleWorker worker = new SelfMediaPublishScheduleWorker(service, router, List.of());
        when(router.platformsByChannel(SelfMediaPlatformPublishChannel.OFFICIAL_API)).thenReturn(Set.of());

        boolean processed = worker.runOnce();

        assertEquals(false, processed);
        verifyNoInteractions(service);
    }

    private SelfMediaPublishScheduleVO schedule(Long id, String platform) {
        SelfMediaPublishScheduleVO vo = new SelfMediaPublishScheduleVO();
        vo.setId(id);
        vo.setPlatform(platform);
        return vo;
    }
}
