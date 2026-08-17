package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountAuthHealthOverviewLabelTest {

    @Test
    void fingerprintBrowserMissingHealthFactDoesNotClaimThatBackendCookieIsRequired() {
        assertEquals("自媒体登录状态待验证",
                AccountAuthHealthOverviewService.alertGroupTitle("self_media", "COOKIE_CREDENTIAL_MISSING"));
        assertEquals(
                "客户账号尚无可信登录健康记录，请在绑定的指纹浏览器环境确认并上报当前登录状态",
                AccountAuthHealthOverviewService.alertGroupSampleMessage(
                        "COOKIE_CREDENTIAL_MISSING",
                        "客户账号平台 Cookie 登录凭据缺失或已失效"
                )
        );
    }
}
