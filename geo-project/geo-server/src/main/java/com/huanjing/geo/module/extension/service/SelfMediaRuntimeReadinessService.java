package com.huanjing.geo.module.extension.service;

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

    private final ExtensionRuntimeStatusMapper extensionRuntimeStatusMapper;
    private final LocalAgentRuntimeStatusMapper localAgentRuntimeStatusMapper;
    private final SelfMediaRuntimeProperties properties;

    public RuntimeReadinessResult evaluate(RuntimeReadinessQuery query) {
        List<String> blockedReasons = new ArrayList<>();
        ExtensionRuntimeStatus extension = latestExtension(query);
        LocalAgentRuntimeStatus helper = latestHelper(query);
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
        if (query == null || query.operatorId() == null) {
            return null;
        }
        List<LocalAgentRuntimeStatus> rows = localAgentRuntimeStatusMapper.selectRecentByOperatorId(query.operatorId(), 1);
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }
}
