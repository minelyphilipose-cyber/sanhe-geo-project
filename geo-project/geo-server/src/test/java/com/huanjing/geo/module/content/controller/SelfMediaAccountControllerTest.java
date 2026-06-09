package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.module.content.douyin.DouyinAuthorizationService;
import com.huanjing.geo.module.content.service.SelfMediaAccountService;
import com.huanjing.geo.module.content.vo.DouyinCapabilityVO;
import com.huanjing.geo.module.content.wechat.WechatMpAuthorizationService;
import com.huanjing.geo.module.customer.service.BrandService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelfMediaAccountControllerTest {

    @Test
    void douyinCapability_returnsServiceCapability() {
        SelfMediaAccountService selfMediaAccountService = mock(SelfMediaAccountService.class);
        BrandService brandService = mock(BrandService.class);
        WechatMpAuthorizationService wechatAuthorizationService = mock(WechatMpAuthorizationService.class);
        DouyinAuthorizationService douyinAuthorizationService = mock(DouyinAuthorizationService.class);
        when(douyinAuthorizationService.capability())
                .thenReturn(new DouyinCapabilityVO(true, "mock", null, false, null, "desc", List.of()));
        SelfMediaAccountController controller = new SelfMediaAccountController(
                selfMediaAccountService,
                brandService,
                wechatAuthorizationService,
                douyinAuthorizationService
        );

        var response = controller.douyinCapability();

        assertEquals(0, response.getCode());
        assertTrue(response.getData().isEnabled());
        assertEquals("mock", response.getData().getMode());
        assertNull(response.getData().getDisabledReason());
    }
}
