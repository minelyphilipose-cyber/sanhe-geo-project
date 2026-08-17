package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.SelfMediaAuthWorkOrder;
import com.huanjing.geo.module.content.mapper.SelfMediaAuthWorkOrderMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaAuthWorkOrderFollowUpServiceTest {

    @Test
    void dueOwnedOrderCreatesReminderForItsOwner() {
        SelfMediaAuthWorkOrderMapper mapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SystemAlertService alertService = mock(SystemAlertService.class);
        SelfMediaAuthWorkOrder due = order(23L, 10L, 7L, "waiting_customer", LocalDateTime.now().minusMinutes(5));
        when(mapper.selectList(any())).thenReturn(List.of(due));
        SelfMediaAuthWorkOrderFollowUpService service = new SelfMediaAuthWorkOrderFollowUpService(mapper, alertService);

        assertEquals(1, service.scanOnce());

        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(alertService).createRecipientAlert(
                eq(SelfMediaAuthWorkOrderFollowUpService.ALERT_TYPE), eq("high"),
                eq("self_media_auth_work_order"), any(), context.capture(), eq(7L), eq(null),
                eq("self_media_auth_work_order_follow_up:23"));
        assertEquals(10L, context.getValue().get("brandId"));
        verify(alertService).resolveOpenByDedupeKeyPrefixExcept(
                eq("self_media_auth_work_order_follow_up:"),
                eq(List.of("self_media_auth_work_order_follow_up:23")), eq(null));
    }

    @Test
    void futureResolvedAndUnownedOrdersCloseAnyStaleReminders() {
        SelfMediaAuthWorkOrderMapper mapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SystemAlertService alertService = mock(SystemAlertService.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                order(23L, 10L, 7L, "waiting_customer", LocalDateTime.now().plusHours(2)),
                order(24L, 11L, 7L, "resolved", LocalDateTime.now().minusHours(1)),
                order(25L, 12L, null, "technical_hold", LocalDateTime.now().minusHours(1))));
        SelfMediaAuthWorkOrderFollowUpService service = new SelfMediaAuthWorkOrderFollowUpService(mapper, alertService);

        assertEquals(0, service.scanOnce());

        verify(alertService).resolveOpenByDedupeKeyPrefixExcept(
                eq("self_media_auth_work_order_follow_up:"), eq(List.of()), eq(null));
    }

    private SelfMediaAuthWorkOrder order(Long id, Long brandId, Long ownerId, String status, LocalDateTime followUpAt) {
        SelfMediaAuthWorkOrder row = new SelfMediaAuthWorkOrder();
        row.setId(id);
        row.setBrandId(brandId);
        row.setOwnerUserId(ownerId);
        row.setStatus(status);
        row.setNextFollowUpAt(followUpAt);
        return row;
    }
}
