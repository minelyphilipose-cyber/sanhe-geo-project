package com.huanjing.geo.module.mobiledashboard.wechat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "geo.mobile-dashboard.wechat-js-sdk",
        name = "client-mode",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockWechatOfficialAccountClient implements WechatOfficialAccountClient {

    @Override
    public AccessTokenResult getAccessToken(String appId, String appSecret) {
        return new AccessTokenResult("mock_mobile_dashboard_access_token", 7200);
    }

    @Override
    public JsapiTicketResult getJsapiTicket(String accessToken) {
        return new JsapiTicketResult("mock_mobile_dashboard_jsapi_ticket", 7200);
    }
}
