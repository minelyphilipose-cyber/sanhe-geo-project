package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.dto.ExtensionVersionCheckResponse;
import com.huanjing.geo.module.extension.entity.ExtensionVersionConfig;
import com.huanjing.geo.module.extension.mapper.ExtensionVersionConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_BAD_REQUEST;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_VERSION_TOO_LOW;

@Service
@RequiredArgsConstructor
public class ExtensionVersionService {

    private static final String DEFAULT_PLATFORM = "chrome";

    private final ExtensionVersionConfigMapper versionConfigMapper;
    private final ExtensionProperties properties;
    private final ExtensionVersionRejectAuditService rejectAuditService;

    public ExtensionVersionCheckResponse check(String platform, String currentVersion) {
        String resolvedPlatform = StringUtils.hasText(platform) ? platform : DEFAULT_PLATFORM;
        validateVersion(currentVersion);
        ExtensionVersionConfig config = versionConfigMapper.selectActiveByPlatform(resolvedPlatform);
        if (config == null) {
            return new ExtensionVersionCheckResponse(true, false, false, null, null, null, null, null);
        }
        boolean belowMin = SemverComparator.compare(currentVersion, config.getMinVersion()) < 0;
        String recommendedVersion = StringUtils.hasText(config.getRecommendedVersion())
                ? config.getRecommendedVersion()
                : config.getLatestVersion();
        boolean belowRecommended = StringUtils.hasText(recommendedVersion)
                && SemverComparator.compare(currentVersion, recommendedVersion) < 0;
        String warning = !belowMin && belowRecommended
                ? "Extension upgrade is recommended"
                : null;
        return new ExtensionVersionCheckResponse(
                !belowMin,
                belowMin,
                !belowMin && belowRecommended,
                config.getMinVersion(),
                recommendedVersion,
                config.getLatestVersion(),
                config.getDownloadUrl(),
                warning
        );
    }

    public ExtensionVersionCheckResponse checkOrThrow(String platform, String currentVersion) {
        ExtensionVersionCheckResponse response;
        try {
            response = check(platform, currentVersion);
        } catch (IllegalArgumentException ex) {
            rejectAuditService.reject(EXTENSION_BAD_REQUEST, "extension version rejected: INVALID_VERSION", ex);
            return null;
        }
        if (!response.supported()) {
            rejectAuditService.reject(
                    EXTENSION_VERSION_TOO_LOW,
                    "extension version too low",
                    403,
                    response,
                    null
            );
        }
        return response;
    }

    public void requireSupported(String platform, String currentVersion) {
        ExtensionVersionCheckResponse response;
        try {
            response = check(platform, currentVersion);
        } catch (IllegalArgumentException ex) {
            rejectAuditService.reject(EXTENSION_BAD_REQUEST, "extension version rejected: INVALID_VERSION", ex);
            return;
        }
        if (!response.supported()) {
            rejectAuditService.reject(EXTENSION_VERSION_TOO_LOW, "extension version too low", 403, response, null);
        }
    }

    private void validateVersion(String currentVersion) {
        if (!StringUtils.hasText(currentVersion)) {
            throw new IllegalArgumentException("extension version is required");
        }
        if (!properties.getVersion().isAllowPrerelease()) {
            SemverComparator.parseToInt(currentVersion);
            return;
        }
        String normalized = currentVersion;
        int buildIndex = normalized.indexOf('+');
        if (buildIndex >= 0) {
            normalized = normalized.substring(0, buildIndex);
        }
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("extension version is required");
        }
    }
}
