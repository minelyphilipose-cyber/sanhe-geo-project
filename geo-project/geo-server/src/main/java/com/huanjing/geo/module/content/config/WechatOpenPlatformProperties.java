package com.huanjing.geo.module.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "geo.wechat.open-platform")
public class WechatOpenPlatformProperties {
    private String componentAppid;
    private String componentAppSecret;
    private String token;
    private String encodingAesKey;
    private boolean draftDistributionEnabled = false;
    private boolean autoPublishEnabled = false;
    private boolean liveVerificationBlocked = true;
    private String liveVerificationReason = "domain_icp_filing_pending";
    private boolean funcScopeStrictMode = false;
    private List<String> allNetworkTestAuthorizerAppids =
            new ArrayList<>(List.of("wx570bc396a51b8ff8"));
    private List<Integer> requiredDraftFuncScopes = new ArrayList<>(List.of(1, 13));
    private List<Integer> requiredMenuFuncScopes = new ArrayList<>(List.of(15));
    private int authType = 1;
    private String backendAuthCallbackUrl;
    private String frontendCallbackUrl;
    private String componentEventUrl;
    private String authorizerMessageUrl;
}
