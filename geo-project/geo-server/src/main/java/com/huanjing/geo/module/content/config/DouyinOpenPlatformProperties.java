package com.huanjing.geo.module.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "geo.douyin.open-platform")
public class DouyinOpenPlatformProperties {
    private String clientKey;
    private String clientSecret;
    private String authPageUrl = "https://open.douyin.com/platform/oauth/connect/";
    private String authCallbackUrl;
    private String frontendCallbackUrl;
    private String webhookUrl;
    private List<String> requiredScopes = new ArrayList<>(List.of("video.create.bind"));
}
