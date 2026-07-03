package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.extension.config.SelfMediaRuntimeProperties;
import com.huanjing.geo.module.extension.dto.RuntimeReadinessQuery;
import com.huanjing.geo.module.extension.dto.RuntimeReadinessResult;
import com.huanjing.geo.module.extension.entity.ExtensionRuntimeStatus;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import com.huanjing.geo.module.extension.mapper.ExtensionRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SelfMediaRuntimeReadinessService {

    public static final String EXTENSION_NOT_SEEN = "EXTENSION_NOT_SEEN";
    public static final String EXTENSION_STALE = "EXTENSION_STALE";
    public static final String HELPER_OFFLINE = "HELPER_OFFLINE";
    public static final String HELPER_CAPACITY_FULL = "HELPER_CAPACITY_FULL";
    public static final String ADSPOWER_API_DOWN = "ADSPOWER_API_DOWN";
    public static final String ACCOUNT_NOT_VERIFIED = "ACCOUNT_NOT_VERIFIED";
    public static final String EXTENSION_VERSION_TOO_LOW = "EXTENSION_VERSION_TOO_LOW";
    public static final String HELPER_VERSION_TOO_LOW = "HELPER_VERSION_TOO_LOW";
    public static final String EXTENSION_CAPABILITY_UNSUPPORTED = "EXTENSION_CAPABILITY_UNSUPPORTED";
    public static final String HELPER_CAPABILITY_UNSUPPORTED = "HELPER_CAPABILITY_UNSUPPORTED";

    private final ExtensionRuntimeStatusMapper extensionRuntimeStatusMapper;
    private final LocalAgentRuntimeStatusMapper localAgentRuntimeStatusMapper;
    private final SelfMediaRuntimeProperties properties;
    private final ObjectMapper objectMapper;

    public RuntimeReadinessResult evaluate(RuntimeReadinessQuery query) {
        return evaluate(query, latestExtension(query), latestHelper(query));
    }

    public RuntimeReadinessResult evaluate(RuntimeReadinessQuery query,
                                           ExtensionRuntimeStatus extension,
                                           LocalAgentRuntimeStatus helper) {
        List<String> blockedReasons = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        if (extension == null) {
            blockedReasons.add(EXTENSION_NOT_SEEN);
        } else {
            LocalDateTime extensionFreshAfter = now.minusMinutes(properties.getGate().getExtensionFreshnessMinutes());
            if (extension.getLastSeenAt() == null || extension.getLastSeenAt().isBefore(extensionFreshAfter)) {
                blockedReasons.add(EXTENSION_STALE);
            }
            if (!"verified".equalsIgnoreCase(extension.getLoginStatus())) {
                blockedReasons.add(ACCOUNT_NOT_VERIFIED);
            }
            if (versionBelow(extension.getExtensionVersion(), properties.getGate().getMinExtensionVersion())) {
                blockedReasons.add(EXTENSION_VERSION_TOO_LOW);
            }
            if (!supportsCapability(extension.getCapabilitiesJson(), query.requiredExtensionFeature())) {
                blockedReasons.add(EXTENSION_CAPABILITY_UNSUPPORTED);
            }
        }

        if (helper == null) {
            blockedReasons.add(HELPER_OFFLINE);
        } else {
            LocalDateTime helperFreshAfter = now.minusMinutes(properties.getGate().getHelperFreshnessMinutes());
            if (helper.getLastSeenAt() == null || helper.getLastSeenAt().isBefore(helperFreshAfter)) {
                blockedReasons.add(HELPER_OFFLINE);
            }
            if (!Boolean.TRUE.equals(helper.getAdspowerApiOk())) {
                blockedReasons.add(ADSPOWER_API_DOWN);
            }
            if (helper.getRunningTaskCount() != null
                    && helper.getCapacity() != null
                    && helper.getRunningTaskCount() >= helper.getCapacity()) {
                blockedReasons.add(HELPER_CAPACITY_FULL);
            }
            if (versionBelow(helper.getHelperVersion(), properties.getGate().getMinHelperVersion())) {
                blockedReasons.add(HELPER_VERSION_TOO_LOW);
            }
            if (!supportsCapability(helper.getCapabilitiesJson(), query.requiredHelperFeature())) {
                blockedReasons.add(HELPER_CAPABILITY_UNSUPPORTED);
            }
        }

        Long extensionId = extension == null ? null : extension.getId();
        Long helperId = helper == null ? null : helper.getId();
        if (blockedReasons.isEmpty()) {
            return RuntimeReadinessResult.ready(extensionId, helperId);
        }
        return RuntimeReadinessResult.blocked(blockedReasons, extensionId, helperId, properties.getGate().getRetryAfterSeconds());
    }

    private ExtensionRuntimeStatus latestExtension(RuntimeReadinessQuery query) {
        if (query == null || query.browserEnvironmentId() == null) {
            return null;
        }
        String platform = StringUtils.hasText(query.platform()) ? query.platform().trim().toLowerCase() : null;
        List<ExtensionRuntimeStatus> rows =
                extensionRuntimeStatusMapper.selectLatestByEnvironmentAndPlatform(query.browserEnvironmentId(), platform);
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private LocalAgentRuntimeStatus latestHelper(RuntimeReadinessQuery query) {
        if (query == null) {
            return null;
        }
        if (query.localAgentSessionId() != null) {
            LocalAgentRuntimeStatus row = localAgentRuntimeStatusMapper.selectLatestBySessionId(query.localAgentSessionId());
            if (row != null) {
                return row;
            }
        }
        if (query.operatorId() == null) {
            return null;
        }
        List<LocalAgentRuntimeStatus> rows = localAgentRuntimeStatusMapper.selectRecentByOperatorId(query.operatorId(), 1);
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private boolean supportsCapability(String capabilitiesJson, String requiredFeature) {
        if (!StringUtils.hasText(requiredFeature)) {
            return true;
        }
        if (!StringUtils.hasText(capabilitiesJson)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(capabilitiesJson);
            JsonNode value = root.path(requiredFeature);
            return value.isBoolean() ? value.asBoolean() : "true".equalsIgnoreCase(value.asText(null));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean versionBelow(String actual, String minimum) {
        if (!StringUtils.hasText(minimum)) {
            return false;
        }
        if (!StringUtils.hasText(actual)) {
            return true;
        }
        return compareVersion(actual, minimum) < 0;
    }

    private int compareVersion(String left, String right) {
        int[] leftParts = versionParts(left);
        int[] rightParts = versionParts(right);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int leftValue = index < leftParts.length ? leftParts[index] : 0;
            int rightValue = index < rightParts.length ? rightParts[index] : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private int[] versionParts(String value) {
        String[] tokens = String.valueOf(value).trim().split("[^0-9]+");
        List<Integer> parts = new ArrayList<>();
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            try {
                parts.add(Integer.parseInt(token));
            } catch (NumberFormatException ignored) {
                parts.add(0);
            }
        }
        return parts.stream().mapToInt(Integer::intValue).toArray();
    }
}
