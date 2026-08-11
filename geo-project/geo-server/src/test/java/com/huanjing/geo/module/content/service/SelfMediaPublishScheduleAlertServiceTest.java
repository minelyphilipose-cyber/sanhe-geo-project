package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleAlert;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleAlertMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaPublishScheduleAlertServiceTest {
    private SelfMediaPublishScheduleMapper scheduleMapper;
    private SelfMediaPublishScheduleAlertMapper alertMapper;
    private LocalAgentSessionMapper localAgentSessionMapper;
    private SelfMediaPublishScheduleAlertService service;

    @BeforeEach
    void setUp() throws Exception {
        scheduleMapper = mock(SelfMediaPublishScheduleMapper.class);
        alertMapper = mock(SelfMediaPublishScheduleAlertMapper.class);
        localAgentSessionMapper = mock(LocalAgentSessionMapper.class);
        service = new SelfMediaPublishScheduleAlertService(
                scheduleMapper,
                alertMapper,
                localAgentSessionMapper,
                new ObjectMapper()
        );
        setField("scanLimit", 100);
        setField("overdueGraceMinutes", 3);
        setField("publishCheckGraceMinutes", 5);
        setField("helperOfflineMinutes", 5);
    }

    @Test
    void reconcileInsertsOverdueAndHelperOfflineAlerts() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 5, 10, 0);
        SelfMediaPublishSchedule row = schedule(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        row.setNextAttemptAt(now.minusMinutes(10));
        when(alertMapper.selectOpenByScheduleId(1L)).thenReturn(List.of());
        when(localAgentSessionMapper.selectActiveByOperatorId(eq(99L), eq(now))).thenReturn(List.of());

        int changed = service.reconcile(row, now);

        assertEquals(2, changed);
        ArgumentCaptor<SelfMediaPublishScheduleAlert> captor = ArgumentCaptor.forClass(SelfMediaPublishScheduleAlert.class);
        verify(alertMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<String> types = captor.getAllValues().stream().map(SelfMediaPublishScheduleAlert::getAlertType).toList();
        assertEquals(List.of(
                SelfMediaPublishScheduleAlertService.TYPE_HELPER_OFFLINE,
                SelfMediaPublishScheduleAlertService.TYPE_SCHEDULE_FILL_OVERDUE
        ), types);
    }

    @Test
    void reconcileResolvesStaleOpenAlertsWhenScheduleIsHealthy() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 5, 10, 0);
        SelfMediaPublishSchedule row = schedule(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED);
        row.setPlatformPublishedUrl("https://mp.toutiao.com/profile_v4/graphic/preview?pgc_id=1");
        SelfMediaPublishScheduleAlert open = new SelfMediaPublishScheduleAlert();
        open.setId(7L);
        open.setScheduleId(1L);
        open.setAlertType(SelfMediaPublishScheduleAlertService.TYPE_PUBLISH_RESULT_UNKNOWN);
        open.setStatus("open");
        open.setActiveKey("1:PUBLISH_RESULT_UNKNOWN");
        when(alertMapper.selectOpenByScheduleId(1L)).thenReturn(List.of(open));

        int changed = service.reconcile(row, now);

        assertEquals(1, changed);
        assertEquals("resolved", open.getStatus());
        assertNull(open.getActiveKey());
        assertEquals(now, open.getResolvedAt());
        verify(alertMapper).updateById(open);
    }

    @Test
    void reconcileInsertsAlertWhenPublishedConfirmedWithoutPublishedUrl() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 5, 10, 0);
        SelfMediaPublishSchedule row = schedule(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED);
        when(alertMapper.selectOpenByScheduleId(1L)).thenReturn(List.of());

        int changed = service.reconcile(row, now);

        assertEquals(1, changed);
        ArgumentCaptor<SelfMediaPublishScheduleAlert> captor = ArgumentCaptor.forClass(SelfMediaPublishScheduleAlert.class);
        verify(alertMapper).insert(captor.capture());
        assertEquals(SelfMediaPublishScheduleAlertService.TYPE_PUBLISH_LINK_MISSING, captor.getValue().getAlertType());
    }

    @Test
    void scanOnceUsesMonitorCandidateQuery() {
        SelfMediaPublishSchedule row = schedule(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        when(scheduleMapper.selectMonitorCandidates(any(), any(), any(), eq(100))).thenReturn(List.of(row));
        when(alertMapper.selectOpenByScheduleId(1L)).thenReturn(List.of());

        int changed = service.scanOnce();

        assertEquals(1, changed);
        verify(alertMapper).insert(any(SelfMediaPublishScheduleAlert.class));
    }

    private SelfMediaPublishSchedule schedule(String status) {
        SelfMediaPublishSchedule row = new SelfMediaPublishSchedule();
        row.setId(1L);
        row.setBrandId(8L);
        row.setArticleId(10L);
        row.setSelfMediaAccountId(20L);
        row.setBrowserEnvironmentId(15L);
        row.setCreatedBy(99L);
        row.setPlatform("toutiao");
        row.setStatus(status);
        return row;
    }

    private void setField(String name, Object value) throws Exception {
        Field field = SelfMediaPublishScheduleAlertService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
