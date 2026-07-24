package com.huanjing.geo.module.mobiledashboard.wechat;

public interface WechatOfficialAccountClient {

    AccessTokenResult getAccessToken(String appId, String appSecret);

    JsapiTicketResult getJsapiTicket(String accessToken);

    record AccessTokenResult(String accessToken, int expiresIn) {
    }

    record JsapiTicketResult(String ticket, int expiresIn) {
    }
}
