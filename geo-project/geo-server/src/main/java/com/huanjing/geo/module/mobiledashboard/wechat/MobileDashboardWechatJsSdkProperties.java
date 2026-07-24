package com.huanjing.geo.module.mobiledashboard.wechat;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Data
@Component
@ConfigurationProperties(prefix = "geo.mobile-dashboard.wechat-js-sdk")
public class MobileDashboardWechatJsSdkProperties {

    private boolean enabled = false;
    private String clientMode = "mock";
    private String appId;
    private String appSecret;
    private List<String> allowedHosts = new ArrayList<>(List.of("www.huanjingaigeo.com"));
    private String shareDescription = "手机数据看板｜查看核心问题监测与内容数据";
    private String shareImageUrl = "https://www.huanjingaigeo.com/favicon.png";
    private String rolloutMode = "allowlist";
    private List<Long> rolloutProjectIds = new ArrayList<>();
    private int signatureRateLimitPerMinute = 20;
    private int errorReportRateLimitPerMinute = 10;

    @PostConstruct
    public void validate() {
        clientMode = normalize(clientMode, "mock");
        rolloutMode = normalize(rolloutMode, "allowlist");
        if (!List.of("mock", "real").contains(clientMode)) {
            throw new IllegalStateException("mobile dashboard WeChat JS-SDK client mode must be mock or real");
        }
        if (!List.of("off", "allowlist", "all").contains(rolloutMode)) {
            throw new IllegalStateException("mobile dashboard WeChat JS-SDK rollout mode must be off, allowlist, or all");
        }
        allowedHosts = allowedHosts == null
                ? new ArrayList<>()
                : allowedHosts.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        rolloutProjectIds = rolloutProjectIds == null
                ? new ArrayList<>()
                : rolloutProjectIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        signatureRateLimitPerMinute = Math.max(1, signatureRateLimitPerMinute);
        errorReportRateLimitPerMinute = Math.max(1, errorReportRateLimitPerMinute);

        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            throw new IllegalStateException("mobile dashboard WeChat JS-SDK AppID/AppSecret must be injected when enabled");
        }
        if (allowedHosts.isEmpty()) {
            throw new IllegalStateException("mobile dashboard WeChat JS-SDK allowed hosts must not be empty");
        }
        validateHttpsUrl(shareImageUrl, "share image URL");
    }

    public boolean isEnabledForProject(Long projectId) {
        if (!enabled || projectId == null || "off".equals(rolloutMode)) {
            return false;
        }
        if ("all".equals(rolloutMode)) {
            return true;
        }
        return rolloutProjectIds.contains(projectId);
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : fallback;
    }

    private void validateHttpsUrl(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("mobile dashboard WeChat JS-SDK " + label + " is required");
        }
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalStateException("mobile dashboard WeChat JS-SDK " + label + " must be an absolute HTTPS URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("mobile dashboard WeChat JS-SDK " + label + " is invalid", ex);
        }
    }
}
