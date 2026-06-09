package com.huanjing.geo.module.content.douyin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.DouyinWebhookEvent;
import com.huanjing.geo.module.content.mapper.DouyinWebhookEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DouyinWebhookServiceTest {

    private final DouyinWebhookEventMapper eventMapper = mock(DouyinWebhookEventMapper.class);
    private final DouyinWebhookService service = new DouyinWebhookService(new ObjectMapper(), eventMapper);

    @Test
    void handleWebhook_returnsUppercaseChallenge() {
        String response = service.handleWebhook("{\"CHALLENGE\":\"verify-token\"}");

        assertEquals("verify-token", response);
        ArgumentCaptor<DouyinWebhookEvent> captor = ArgumentCaptor.forClass(DouyinWebhookEvent.class);
        verify(eventMapper).insert(captor.capture());
        assertEquals("verify-token", captor.getValue().getChallenge());
        assertEquals("received", captor.getValue().getProcessStatus());
    }

    @Test
    void handleWebhook_returnsLowercaseChallenge() {
        String response = service.handleWebhook("{\"challenge\":\"verify-token\"}");

        assertEquals("verify-token", response);
    }

    @Test
    void handleWebhook_returnsSuccessForEventPayload() {
        String response = service.handleWebhook("{\"event\":\"item_status_change\",\"item_id\":\"123\"}");

        assertEquals("success", response);
        ArgumentCaptor<DouyinWebhookEvent> captor = ArgumentCaptor.forClass(DouyinWebhookEvent.class);
        verify(eventMapper).insert(captor.capture());
        assertEquals("item_status_change", captor.getValue().getEventType());
    }

    @Test
    void handleWebhook_returnsSuccessForInvalidJson() {
        String response = service.handleWebhook("not-json");

        assertEquals("success", response);
    }
}
