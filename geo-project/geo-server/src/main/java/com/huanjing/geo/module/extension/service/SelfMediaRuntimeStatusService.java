package com.huanjing.geo.module.extension.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.BrowserEnvironment;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.extension.dto.ExtensionRuntimeStatusReportRequest;
import com.huanjing.geo.module.extension.dto.ExtensionRuntimeStatusVO;
import com.huanjing.geo.module.extension.dto.LocalAgentRuntimeStatusReportRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentRuntimeStatusVO;
import com.huanjing.geo.module.extension.entity.ExtensionRuntimeStatus;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.ExtensionRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SelfMediaRuntimeStatusService {

    private static final int ERROR_CODE = 70044;

    private final ExtensionRuntimeStatusMapper extensionRuntimeStatusMapper;
    private final LocalAgentRuntimeStatusMapper localAgentRuntimeStatusMapper;
    private final BrowserEnvironmentMapper browserEnvironmentMapper;
    private final BrowserEnvironmentAccountMapper browserEnvironmentAccountMapper;
    private final ObjectMapper objectMapper;

    public ExtensionRuntimeStatusVO reportExtension(ExtensionSession session,
                                                    ExtensionRuntimeStatusReportRequest request) {
        if (request == null) {
            badRequest("REQUEST_REQUIRED", "runtime status request is required");
        }
        String providerProfileId = required(request.providerProfileId(), "PROVIDER_PROFILE_ID_REQUIRED");
        String installId = required(request.installId(), "INSTALL_ID_REQUIRED");
        String extensionVersion = firstText(request.extensionVersion(), session == null ? null : session.getExtensionVersion());
        if (!StringUtils.hasText(extensionVersion)) {
            badRequest("EXTENSION_VERSION_REQUIRED", "extensionVersion is required");
        }

        LocalDateTime now = LocalDateTime.now();
        BrowserEnvironment environment = resolveEnvironment(providerProfileId, request.environmentKey());
        String platform = normalizePlatform(firstText(request.detectedPlatform(), request.platform()));
        BrowserEnvironmentAccount account = resolveEnvironmentAccount(environment, platform);
        enforceSessionBrand(session, environment, account);

        ExtensionRuntimeStatus row = new ExtensionRuntimeStatus();
        row.setInstallId(installId);
        row.setExtensionSessionId(session == null ? null : session.getId());
        row.setBrowserEnvironmentId(environment == null ? null : environment.getId());
        row.setBrowserEnvironmentAccountId(account == null ? null : account.getId());
        row.setBrandId(firstNonNull(
                account == null ? null : account.getBrandId(),
                environment == null ? null : environment.getBrandId(),
                session == null ? null : session.getBrandId()
        ));
        row.setPlatform(platform);
        row.setEnvironmentKey(firstText(request.environmentKey(), environment == null ? null : environment.getEnvironmentKey()));
        row.setProviderProfileId(providerProfileId);
        row.setExtensionVersion(limit(extensionVersion, 32));
        row.setProtocolVersion(limit(firstText(request.protocolVersion(), "1"), 32));
        row.setCurrentUrl(limit(request.currentUrl(), 1024));
        row.setDetectedPlatform(platform);
        row.setDetectedAccountName(limit(request.detectedAccountName(), 255));
        row.setDetectedPlatformAccountId(limit(request.detectedPlatformAccountId(), 128));
        row.setLoginStatus(limit(firstText(request.loginStatus(), "unknown"), 32));
        row.setRuntimeStage(limit(request.runtimeStage(), 64));
        row.setRuntimeStageAt(request.runtimeStageAt());
        row.setRuntimeStageMessage(limit(request.runtimeStageMessage(), 512));
        row.setCapabilitiesJson(json(request.capabilities()));
        row.setLastTaskId(request.lastTaskId());
        row.setLastErrorCode(limit(request.lastErrorCode(), 128));
        row.setLastErrorMessage(limit(request.lastErrorMessage(), 512));
        row.setLastSeenAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);

        upsertExtension(row);
        return ExtensionRuntimeStatusVO.from(row);
    }

    public LocalAgentRuntimeStatusVO reportLocalAgent(LocalAgentSession session,
                                                     LocalAgentRuntimeStatusReportRequest request) {
        if (request == null) {
            badRequest("REQUEST_REQUIRED", "runtime status request is required");
        }
        String machineId = required(request.machineId(), "MACHINE_ID_REQUIRED");
        String activeProfile = required(request.activeProfile(), "ACTIVE_PROFILE_REQUIRED");
        String helperVersion = required(request.helperVersion(), "HELPER_VERSION_REQUIRED");
        LocalDateTime now = LocalDateTime.now();

        LocalAgentRuntimeStatus row = new LocalAgentRuntimeStatus();
        row.setMachineId(machineId);
        row.setActiveProfile(activeProfile);
        row.setSessionId(session == null ? null : session.getId());
        row.setOperatorId(session == null ? null : session.getOperatorId());
        row.setHelperVersion(limit(helperVersion, 32));
        row.setProtocolVersion(limit(firstText(request.protocolVersion(), "1"), 32));
        row.setHelperName(limit(firstText(request.helperName(), session == null ? null : session.getHelperName()), 128));
        row.setAdspowerApiOk(Boolean.TRUE.equals(request.adspowerApiOk()));
        row.setAdspowerApiBase(limit(request.adspowerApiBase(), 255));
        row.setRunningTaskCount(Math.max(0, request.runningTaskCount() == null ? 0 : request.runningTaskCount()));
        row.setCapacity(Math.max(1, request.capacity() == null ? 1 : request.capacity()));
        row.setSupportedPlatformsJson(json(request.supportedPlatforms()));
        row.setCapabilitiesJson(json(request.capabilities()));
        row.setLastErrorCode(limit(request.lastErrorCode(), 128));
        row.setLastErrorMessage(limit(request.lastErrorMessage(), 512));
        row.setLastSeenAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);

        upsertLocalAgent(row);
        return LocalAgentRuntimeStatusVO.from(row);
    }

    private void upsertExtension(ExtensionRuntimeStatus row) {
        try {
            extensionRuntimeStatusMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            extensionRuntimeStatusMapper.updateByProviderProfileIdAndInstallId(row);
            ExtensionRuntimeStatus current = extensionRuntimeStatusMapper.selectOne(new LambdaQueryWrapper<ExtensionRuntimeStatus>()
                    .eq(ExtensionRuntimeStatus::getProviderProfileId, row.getProviderProfileId())
                    .eq(ExtensionRuntimeStatus::getInstallId, row.getInstallId())
                    .last("LIMIT 1"));
            if (current != null) {
                row.setId(current.getId());
                row.setCreatedAt(current.getCreatedAt());
            }
        }
    }

    private void upsertLocalAgent(LocalAgentRuntimeStatus row) {
        try {
            localAgentRuntimeStatusMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            localAgentRuntimeStatusMapper.updateByMachineIdAndActiveProfile(row);
            LocalAgentRuntimeStatus current = localAgentRuntimeStatusMapper.selectOne(new LambdaQueryWrapper<LocalAgentRuntimeStatus>()
                    .eq(LocalAgentRuntimeStatus::getMachineId, row.getMachineId())
                    .eq(LocalAgentRuntimeStatus::getActiveProfile, row.getActiveProfile())
                    .last("LIMIT 1"));
            if (current != null) {
                row.setId(current.getId());
                row.setCreatedAt(current.getCreatedAt());
            }
        }
    }

    private BrowserEnvironment resolveEnvironment(String providerProfileId, String environmentKey) {
        BrowserEnvironment byProvider = browserEnvironmentMapper.selectOne(new LambdaQueryWrapper<BrowserEnvironment>()
                .eq(BrowserEnvironment::getProviderProfileId, providerProfileId)
                .isNull(BrowserEnvironment::getDeletedAt)
                .last("LIMIT 1"));
        if (byProvider != null || !StringUtils.hasText(environmentKey)) {
            return byProvider;
        }
        return browserEnvironmentMapper.selectOne(new LambdaQueryWrapper<BrowserEnvironment>()
                .and(wrapper -> wrapper
                        .eq(BrowserEnvironment::getEnvironmentKey, environmentKey.trim())
                        .or()
                        .eq(BrowserEnvironment::getName, environmentKey.trim()))
                .isNull(BrowserEnvironment::getDeletedAt)
                .last("LIMIT 1"));
    }

    private BrowserEnvironmentAccount resolveEnvironmentAccount(BrowserEnvironment environment, String platform) {
        if (environment == null || !StringUtils.hasText(platform)) {
            return null;
        }
        return browserEnvironmentAccountMapper.selectOne(new LambdaQueryWrapper<BrowserEnvironmentAccount>()
                .eq(BrowserEnvironmentAccount::getBrowserEnvironmentId, environment.getId())
                .eq(BrowserEnvironmentAccount::getPlatform, platform)
                .isNull(BrowserEnvironmentAccount::getDeletedAt)
                .last("LIMIT 1"));
    }

    private void enforceSessionBrand(ExtensionSession session, BrowserEnvironment environment, BrowserEnvironmentAccount account) {
        if (session == null || session.getBrandId() == null) {
            return;
        }
        if (environment != null && environment.getBrandId() != null && !Objects.equals(environment.getBrandId(), session.getBrandId())) {
            throw new BizException(ERROR_CODE, "browser environment brand mismatch", 403, Map.of("code", "BROWSER_ENVIRONMENT_BRAND_MISMATCH"));
        }
        if (account != null && account.getBrandId() != null && !Objects.equals(account.getBrandId(), session.getBrandId())) {
            throw new BizException(ERROR_CODE, "browser environment account brand mismatch", 403, Map.of("code", "BROWSER_ENVIRONMENT_ACCOUNT_BRAND_MISMATCH"));
        }
    }

    private String required(String value, String code) {
        if (!StringUtils.hasText(value)) {
            badRequest(code, code);
        }
        return value.trim();
    }

    private void badRequest(String code, String message) {
        throw new BizException(ERROR_CODE, message, 400, Map.of("code", code));
    }

    private String normalizePlatform(String platform) {
        return StringUtils.hasText(platform) ? platform.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String json(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new BizException(ERROR_CODE, "runtime status json serialization failed", 400, Map.of("code", "RUNTIME_STATUS_JSON_INVALID"), ex);
        }
    }
}
