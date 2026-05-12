package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.wechat.WechatOpenPlatformClient.QueryAuthResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WechatQueryAuthCodeAsyncService {
    private static final long TIMEOUT_SECONDS = 8;

    private final WechatMpClient wechatMpClient;
    private final WechatOpenPlatformClient openPlatformClient;
    private final WechatComponentAccessTokenService componentAccessTokenService;
    private final WechatOpenPlatformProperties properties;
    private final Executor wechatCallbackExecutor;

    public WechatQueryAuthCodeAsyncService(WechatMpClient wechatMpClient,
                                           WechatOpenPlatformClient openPlatformClient,
                                           WechatComponentAccessTokenService componentAccessTokenService,
                                           WechatOpenPlatformProperties properties,
                                           @Qualifier("wechatCallbackExecutor") Executor wechatCallbackExecutor) {
        this.wechatMpClient = wechatMpClient;
        this.openPlatformClient = openPlatformClient;
        this.componentAccessTokenService = componentAccessTokenService;
        this.properties = properties;
        this.wechatCallbackExecutor = wechatCallbackExecutor;
    }

    public void handle(String authorizerAppid, String openid, String authCode, long startedAt) {
        CompletableFuture.runAsync(() -> handleSafely(authorizerAppid, openid, authCode, startedAt), wechatCallbackExecutor)
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.warn("WeChat QUERY_AUTH_CODE async task timed out or failed authorizerAppid={} openid={} authCode={}",
                            authorizerAppid, openid, mask(authCode), ex);
                    return null;
                });
    }

    void handleSafely(String authorizerAppid, String openid, String authCode, long startedAt) {
        try {
            long queryStart = System.currentTimeMillis();
            String componentToken = componentAccessTokenService.getAccessToken();
            QueryAuthResult auth = openPlatformClient.queryAuth(componentToken, properties.getComponentAppid(), authCode);
            long queryEnd = System.currentTimeMillis();
            wechatMpClient.sendCustomTextMessage(auth.authorizerAccessToken(), openid, authCode + "_from_api");
            long end = System.currentTimeMillis();
            log.info("WeChat QUERY_AUTH_CODE handled authorizerAppid={} openid={} authCode={} queryAuthMs={} customMsgMs={} totalMs={}",
                    authorizerAppid, openid, mask(authCode), queryEnd - queryStart, end - queryEnd, end - startedAt);
        } catch (Exception ex) {
            log.warn("WeChat QUERY_AUTH_CODE async handling failed authorizerAppid={} openid={} authCode={}",
                    authorizerAppid, openid, mask(authCode), ex);
        }
    }

    private String mask(String authCode) {
        if (authCode == null || authCode.length() <= 8) {
            return "****";
        }
        return authCode.substring(0, 4) + "****" + authCode.substring(authCode.length() - 4);
    }
}
