package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.module.content.douyin.DouyinWebhookService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinWebhookControllerTest {

    @Test
    void receiveWebhook_returnsServiceResponse() {
        DouyinWebhookService service = mock(DouyinWebhookService.class);
        when(service.handleWebhook("{\"CHALLENGE\":\"abc\"}")).thenReturn("abc");
        DouyinWebhookController controller = new DouyinWebhookController(service);

        String response = controller.receiveWebhook("{\"CHALLENGE\":\"abc\"}");

        assertEquals("abc", response);
        verify(service).handleWebhook("{\"CHALLENGE\":\"abc\"}");
    }

    @Test
    void receiveWebhook_returnsSuccessWhenServiceThrows() {
        DouyinWebhookService service = mock(DouyinWebhookService.class);
        when(service.handleWebhook("bad")).thenThrow(new IllegalStateException("failed"));
        DouyinWebhookController controller = new DouyinWebhookController(service);

        String response = controller.receiveWebhook("bad");

        assertEquals("success", response);
    }
}
