package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.douyin.DouyinAuthorizationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DouyinAuthorizationControllerTest {

    @Test
    void callbackPropagatesOAuthRetirementAsGone() {
        DouyinAuthorizationService service = mock(DouyinAuthorizationService.class);
        when(service.handleCallback("code", "state"))
                .thenThrow(new BizException(410, DouyinAuthorizationService.RETIRED_MESSAGE, 410, null));
        DouyinAuthorizationController controller = new DouyinAuthorizationController(service);

        BizException exception = assertThrows(BizException.class,
                () -> controller.authCallback("code", "state", null, null));

        assertEquals(410, exception.getCode());
        assertEquals(410, exception.getHttpStatus());
    }
}
