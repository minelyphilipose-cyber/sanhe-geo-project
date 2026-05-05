package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.module.content.douyin.DouyinAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinAuthorizationControllerTest {

    @Test
    void authCallback_successRedirectsToServiceLocation() {
        DouyinAuthorizationService service = mock(DouyinAuthorizationService.class);
        when(service.handleCallback("code", "state")).thenReturn("http://front/callback?douyinAuth=success");
        DouyinAuthorizationController controller = new DouyinAuthorizationController(service);

        var response = controller.authCallback("code", "state", null, null);

        assertEquals(302, response.getStatusCode().value());
        assertEquals("http://front/callback?douyinAuth=success", response.getHeaders().getFirst(HttpHeaders.LOCATION));
        verify(service).handleCallback("code", "state");
    }

    @Test
    void authCallback_errorParamRedirectsWithoutCodeExchange() {
        DouyinAuthorizationService service = mock(DouyinAuthorizationService.class);
        when(service.errorRedirect("access_denied", "deny")).thenReturn("http://front/callback?douyinAuth=callback_failed");
        DouyinAuthorizationController controller = new DouyinAuthorizationController(service);

        var response = controller.authCallback(null, "state", "access_denied", "deny");

        assertEquals(302, response.getStatusCode().value());
        assertEquals("http://front/callback?douyinAuth=callback_failed", response.getHeaders().getFirst(HttpHeaders.LOCATION));
        verify(service).errorRedirect("access_denied", "deny");
    }

    @Test
    void authCallback_exceptionRedirectsToCallbackFailed() {
        DouyinAuthorizationService service = mock(DouyinAuthorizationService.class);
        when(service.handleCallback("code", "bad-state")).thenThrow(new IllegalStateException("bad state"));
        when(service.errorRedirect("callback_failed", "bad state")).thenReturn("http://front/callback?douyinAuth=callback_failed");
        DouyinAuthorizationController controller = new DouyinAuthorizationController(service);

        var response = controller.authCallback("code", "bad-state", null, null);

        assertEquals(302, response.getStatusCode().value());
        assertEquals("http://front/callback?douyinAuth=callback_failed", response.getHeaders().getFirst(HttpHeaders.LOCATION));
        verify(service).errorRedirect("callback_failed", "bad state");
    }
}
