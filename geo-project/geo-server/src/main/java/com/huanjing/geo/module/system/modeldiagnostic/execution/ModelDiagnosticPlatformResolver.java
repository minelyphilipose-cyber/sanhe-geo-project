package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.transport.PollPayloadProtector;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticModelTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ModelDiagnosticPlatformResolver {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 180_000;

    private final AiPlatformConfigMapper platformMapper;
    private final PollPayloadProtector payloadProtector;
    private final ObjectMapper objectMapper;

    public ModelDiagnosticPlatformProfile resolve(Long platformConfigId, ModelDiagnosticMode mode) {
        return resolve(platformConfigId, mode, ModelDiagnosticModelTier.PRIMARY);
    }

    public ModelDiagnosticPlatformProfile resolve(Long platformConfigId,
                                                   ModelDiagnosticMode mode,
                                                   ModelDiagnosticModelTier modelTier) {
        if (platformConfigId == null || mode == null) {
            throw invalid("Platform configuration and diagnostic mode are required");
        }
        modelTier = modelTier == null ? ModelDiagnosticModelTier.PRIMARY : modelTier;
        AiPlatformConfig config = platformMapper.selectById(platformConfigId);
        if (config == null) {
            throw invalid("Platform configuration does not exist: " + platformConfigId);
        }

        IntegrationType integrationType = integrationType(config.getIntegrationType());
        if (mode == ModelDiagnosticMode.BASIC_CHAT && integrationType != IntegrationType.OPENAI_CHAT) {
            throw invalid("BASIC_CHAT requires an OPENAI_CHAT configuration");
        }
        if (mode == ModelDiagnosticMode.WEB_SEARCH && !integrationType.isWebSearch()) {
            throw invalid("WEB_SEARCH requires a web-search configuration");
        }
        requireHttpUrl(config.getApiUrl());
        String requestedModelId = modelTier == ModelDiagnosticModelTier.LOW
                ? config.getLowModelId() : config.getModelId();
        if (!StringUtils.hasText(requestedModelId)) {
            throw invalid(modelTier == ModelDiagnosticModelTier.LOW
                    ? "Platform low-performance model ID is required"
                    : "Platform primary model ID is required");
        }

        String providerConfig = StringUtils.hasText(config.getProviderConfigJson())
                ? config.getProviderConfigJson() : "{}";
        String sanitizedProviderConfig = payloadProtector.sanitize(providerConfig);
        String snapshotJson = snapshot(
                config, integrationType, modelTier, requestedModelId, sanitizedProviderConfig);
        int requestTimeoutMs = positiveOrDefault(config.getTimeoutMs(), DEFAULT_REQUEST_TIMEOUT_MS);
        int connectTimeoutMs = Math.min(DEFAULT_CONNECT_TIMEOUT_MS, requestTimeoutMs);

        return new ModelDiagnosticPlatformProfile(
                config.getId(),
                config.getPlatformCode(),
                StringUtils.hasText(config.getChannelCode())
                        ? config.getChannelCode() : config.getPlatformCode(),
                config.getPlatformName(),
                StringUtils.hasText(config.getUsageScene()) ? config.getUsageScene() : "UNSPECIFIED",
                integrationType,
                config.getApiUrl().trim(),
                requestedModelId.trim(),
                config.getConfigVersion() == null ? 1L : config.getConfigVersion(),
                providerConfig,
                snapshotJson,
                sha256(snapshotJson),
                config.getPrimaryKeyRef(),
                config.getApiKey(),
                connectTimeoutMs,
                requestTimeoutMs
        );
    }

    private String snapshot(AiPlatformConfig config,
                            IntegrationType integrationType,
                            ModelDiagnosticModelTier modelTier,
                            String requestedModelId,
                            String sanitizedProviderConfig) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("platformConfigId", config.getId());
        snapshot.put("platformCode", config.getPlatformCode());
        snapshot.put("channelCode", config.getChannelCode());
        snapshot.put("platformName", config.getPlatformName());
        snapshot.put("usageScene", config.getUsageScene());
        snapshot.put("integrationType", integrationType.name());
        snapshot.put("configVersion", config.getConfigVersion() == null ? 1L : config.getConfigVersion());
        snapshot.put("endpointUrl", config.getApiUrl());
        snapshot.put("modelTier", modelTier.name());
        snapshot.put("modelId", requestedModelId);
        snapshot.put("primaryKeyRef", config.getPrimaryKeyRef());
        snapshot.put("providerConfig", sanitizedProviderConfig);
        snapshot.put("enabled", config.getEnabled());
        snapshot.put("timeoutMs", config.getTimeoutMs());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new ModelDiagnosticExecutionException(
                    ErrorCategory.INTERNAL_ERROR, null,
                    "Failed to serialize diagnostic configuration snapshot", null, null, ex);
        }
    }

    private IntegrationType integrationType(String value) {
        try {
            return IntegrationType.valueOf(value);
        } catch (Exception ex) {
            throw invalid("Unsupported platform integration type: " + value);
        }
    }

    private void requireHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            boolean http = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            if (!http || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw invalid("Platform endpoint must be a valid HTTP(S) URL");
        }
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash diagnostic configuration", ex);
        }
    }

    private ModelDiagnosticExecutionException invalid(String message) {
        return new ModelDiagnosticExecutionException(
                ErrorCategory.INVALID_REQUEST, null, message, null, null, null);
    }
}
