package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatMpClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.wechat.client", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockWechatOpenPlatformClient implements WechatOpenPlatformClient {
    private static final String DEFAULT_FUNC_INFO = "[{\"funcscope_category\":{\"id\":1}},{\"funcscope_category\":{\"id\":13}}]";

    private final WechatMpClientProperties properties;

    @Override
    public ComponentAccessTokenResult getComponentAccessToken(String componentAppid, String componentAppSecret, String ticket) {
        failIf("component_token_failed", 40001, "invalid component credential");
        return new ComponentAccessTokenResult("mock_component_access_token", 7200);
    }

    @Override
    public PreAuthCodeResult createPreAuthCode(String componentAccessToken, String componentAppid) {
        failIf("pre_auth_code_failed", 40001, "invalid component access token");
        return new PreAuthCodeResult("mock_pre_auth_code", 600);
    }

    @Override
    public QueryAuthResult queryAuth(String componentAccessToken, String componentAppid, String authCode) {
        failIf("invalid_credential", 40001, "invalid credential");
        return new QueryAuthResult(
                "mock_authorizer_appid",
                "mock_authorizer_access_token",
                "mock_authorizer_refresh_token",
                7200,
                DEFAULT_FUNC_INFO
        );
    }

    @Override
    public AuthorizerInfoResult getAuthorizerInfo(String componentAccessToken, String componentAppid, String authorizerAppid) {
        failIf("authorizer_info_failed", 61003, "authorizer not exist");
        return new AuthorizerInfoResult(
                "Mock 微信公众号",
                "https://mock.local/head.png",
                "https://mock.local/qrcode.png",
                "Mock Principal",
                "0",
                DEFAULT_FUNC_INFO
        );
    }

    @Override
    public AuthorizerTokenResult refreshAuthorizerToken(String componentAccessToken,
                                                        String componentAppid,
                                                        String authorizerAppid,
                                                        String refreshToken) {
        failIf("invalid_credential", 40001, "invalid credential");
        return new AuthorizerTokenResult("mock_refreshed_access_token", "mock_refreshed_refresh_token", 7200);
    }

    private void failIf(String fault, int code, String message) {
        if (fault.equals(properties.getFault())) {
            throw new BizException(code, message);
        }
    }
}
