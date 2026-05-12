package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatQueryAuthCodeAsyncServiceTest {

    private final WechatMpClient wechatMpClient = mock(WechatMpClient.class);
    private final WechatOpenPlatformClient openPlatformClient = mock(WechatOpenPlatformClient.class);
    private final WechatComponentAccessTokenService componentAccessTokenService = mock(WechatComponentAccessTokenService.class);
    private final WechatOpenPlatformProperties properties = new WechatOpenPlatformProperties();

    private WechatQueryAuthCodeAsyncService service;

    @BeforeEach
    void setUp() {
        properties.setComponentAppid("component-appid");
        when(componentAccessTokenService.getAccessToken()).thenReturn("component-token");
        service = new WechatQueryAuthCodeAsyncService(
                wechatMpClient,
                openPlatformClient,
                componentAccessTokenService,
                properties,
                Runnable::run
        );
    }

    @Test
    void handleSafelyQueriesAuthAndSendsCustomMessage() {
        when(openPlatformClient.queryAuth("component-token", "component-appid", "queryauthcode@@@12345678"))
                .thenReturn(new WechatOpenPlatformClient.QueryAuthResult(
                        "wx-authorizer",
                        "authorizer-token",
                        "refresh-token",
                        7200,
                        "[]"
                ));

        service.handleSafely("wx-authorizer", "from-openid", "queryauthcode@@@12345678", System.currentTimeMillis());

        verify(openPlatformClient).queryAuth("component-token", "component-appid", "queryauthcode@@@12345678");
        verify(wechatMpClient).sendCustomTextMessage(
                "authorizer-token",
                "from-openid",
                "queryauthcode@@@12345678_from_api"
        );
    }

    @Test
    void handleSafelySwallowsQueryAuthFailure() {
        when(openPlatformClient.queryAuth("component-token", "component-appid", "queryauthcode@@@12345678"))
                .thenThrow(new RuntimeException("wechat down"));

        assertDoesNotThrow(() -> service.handleSafely(
                "wx-authorizer",
                "from-openid",
                "queryauthcode@@@12345678",
                System.currentTimeMillis()
        ));
    }

    @Test
    void handleSafelySwallowsCustomMessageFailure() {
        when(openPlatformClient.queryAuth("component-token", "component-appid", "queryauthcode@@@12345678"))
                .thenReturn(new WechatOpenPlatformClient.QueryAuthResult(
                        "wx-authorizer",
                        "authorizer-token",
                        "refresh-token",
                        7200,
                        "[]"
                ));
        org.mockito.Mockito.doThrow(new RuntimeException("custom send failed"))
                .when(wechatMpClient)
                .sendCustomTextMessage("authorizer-token", "from-openid", "queryauthcode@@@12345678_from_api");

        assertDoesNotThrow(() -> service.handleSafely(
                "wx-authorizer",
                "from-openid",
                "queryauthcode@@@12345678",
                System.currentTimeMillis()
        ));
    }
}
