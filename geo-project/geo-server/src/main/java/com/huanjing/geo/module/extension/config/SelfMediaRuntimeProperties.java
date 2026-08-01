package com.huanjing.geo.module.extension.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "geo.self-media-runtime")
public class SelfMediaRuntimeProperties {

    private Gate gate = new Gate();

    @Data
    public static class Gate {
        private String defaultMode = "block_non_destructive";
        private int extensionFreshnessMinutes = 10;
        private int helperFreshnessMinutes = 2;
        private int retryAfterSeconds = 30;
        private String minExtensionVersion;
        private String minHelperVersion;
        private GateRule global = new GateRule();
        private Map<String, GateRule> platforms = new HashMap<>();
        private Map<Long, BrandRule> brands = new HashMap<>();

        public String modeFor(Long brandId, String platform) {
            String normalizedPlatform = normalizePlatform(platform);
            BrandRule brandRule = brandId == null ? null : brands.get(brandId);
            if (brandRule != null) {
                GateRule brandPlatformRule = normalizedPlatform == null ? null : brandRule.getPlatforms().get(normalizedPlatform);
                if (hasMode(brandPlatformRule)) {
                    return brandPlatformRule.getMode();
                }
                if (StringUtils.hasText(brandRule.getMode())) {
                    return brandRule.getMode();
                }
            }
            GateRule platformRule = normalizedPlatform == null ? null : platforms.get(normalizedPlatform);
            if (hasMode(platformRule)) {
                return platformRule.getMode();
            }
            if (hasMode(global)) {
                return global.getMode();
            }
            return defaultMode;
        }

        public String minExtensionVersionFor(String platform) {
            GateRule platformRule = ruleFor(platform);
            if (platformRule != null && StringUtils.hasText(platformRule.getMinExtensionVersion())) {
                return platformRule.getMinExtensionVersion();
            }
            return minExtensionVersion;
        }

        public String minHelperVersionFor(String platform) {
            GateRule platformRule = ruleFor(platform);
            if (platformRule != null && StringUtils.hasText(platformRule.getMinHelperVersion())) {
                return platformRule.getMinHelperVersion();
            }
            return minHelperVersion;
        }

        private GateRule ruleFor(String platform) {
            String normalizedPlatform = normalizePlatform(platform);
            return normalizedPlatform == null ? null : platforms.get(normalizedPlatform);
        }

        private static boolean hasMode(GateRule rule) {
            return rule != null && StringUtils.hasText(rule.getMode());
        }

        private static String normalizePlatform(String platform) {
            return StringUtils.hasText(platform) ? platform.trim().toLowerCase(Locale.ROOT) : null;
        }
    }

    @Data
    public static class GateRule {
        private String mode;
        private String minExtensionVersion;
        private String minHelperVersion;
    }

    @Data
    public static class BrandRule {
        private String mode;
        private Map<String, GateRule> platforms = new HashMap<>();
    }
}
