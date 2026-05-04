package com.huanjing.geo.module.content.wechat;

public interface WechatOpenPlatformClient {
    ComponentAccessTokenResult getComponentAccessToken(String componentAppid, String componentAppSecret, String ticket);

    PreAuthCodeResult createPreAuthCode(String componentAccessToken, String componentAppid);

    QueryAuthResult queryAuth(String componentAccessToken, String componentAppid, String authCode);

    AuthorizerInfoResult getAuthorizerInfo(String componentAccessToken, String componentAppid, String authorizerAppid);

    AuthorizerTokenResult refreshAuthorizerToken(String componentAccessToken,
                                                 String componentAppid,
                                                 String authorizerAppid,
                                                 String refreshToken);

    record ComponentAccessTokenResult(String accessToken, int expiresIn) {
    }

    record PreAuthCodeResult(String preAuthCode, int expiresIn) {
    }

    record QueryAuthResult(
            String authorizerAppid,
            String authorizerAccessToken,
            String authorizerRefreshToken,
            int expiresIn,
            String funcInfoJson
    ) {
    }

    record AuthorizerInfoResult(
            String accountName,
            String headImg,
            String qrcodeUrl,
            String principalName,
            String verifyTypeInfo,
            String funcInfoJson
    ) {
    }

    record AuthorizerTokenResult(String authorizerAccessToken, String authorizerRefreshToken, int expiresIn) {
    }
}
