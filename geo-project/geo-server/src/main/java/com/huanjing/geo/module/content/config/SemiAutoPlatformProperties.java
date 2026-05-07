package com.huanjing.geo.module.content.config;

import com.huanjing.geo.module.content.service.adapter.PlatformFillProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "semi-auto.platforms")
public class SemiAutoPlatformProperties {

    private Map<String, Platform> profiles = new LinkedHashMap<>();

    public Map<String, Platform> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, Platform> profiles) {
        this.profiles = profiles;
    }

    public PlatformFillProfile profile(String platform) {
        Platform config = profiles == null ? null : profiles.get(platform);
        if (config == null) {
            throw new IllegalStateException("semi-auto platform profile is missing: " + platform);
        }
        if (!StringUtils.hasText(config.getPublishUrl())) {
            throw new IllegalStateException("semi-auto publishUrl is required: " + platform);
        }
        if (config.getAllowedHtmlTags() == null || config.getAllowedHtmlTags().isEmpty()) {
            throw new IllegalStateException("semi-auto allowedHtmlTags must not be empty: " + platform);
        }
        return new PlatformFillProfile(
                platform,
                config.getPublishUrl(),
                config.getCookieDomains(),
                config.getRequiredCookieNames(),
                config.getEditorSelectors(),
                config.getAllowedHtmlTags(),
                config.getPlatformOptions()
        );
    }

    public static class Platform {
        private String publishUrl;
        private List<String> cookieDomains = List.of();
        private List<String> requiredCookieNames = List.of();
        private Map<String, String> editorSelectors = Map.of();
        private List<String> allowedHtmlTags = List.of();
        private Map<String, Object> platformOptions = Map.of();

        public String getPublishUrl() {
            return publishUrl;
        }

        public void setPublishUrl(String publishUrl) {
            this.publishUrl = publishUrl;
        }

        public List<String> getCookieDomains() {
            return cookieDomains;
        }

        public void setCookieDomains(List<String> cookieDomains) {
            this.cookieDomains = cookieDomains;
        }

        public List<String> getRequiredCookieNames() {
            return requiredCookieNames;
        }

        public void setRequiredCookieNames(List<String> requiredCookieNames) {
            this.requiredCookieNames = requiredCookieNames;
        }

        public Map<String, String> getEditorSelectors() {
            return editorSelectors;
        }

        public void setEditorSelectors(Map<String, String> editorSelectors) {
            this.editorSelectors = editorSelectors;
        }

        public List<String> getAllowedHtmlTags() {
            return allowedHtmlTags;
        }

        public void setAllowedHtmlTags(List<String> allowedHtmlTags) {
            this.allowedHtmlTags = allowedHtmlTags;
        }

        public Map<String, Object> getPlatformOptions() {
            return platformOptions;
        }

        public void setPlatformOptions(Map<String, Object> platformOptions) {
            this.platformOptions = platformOptions;
        }
    }
}
