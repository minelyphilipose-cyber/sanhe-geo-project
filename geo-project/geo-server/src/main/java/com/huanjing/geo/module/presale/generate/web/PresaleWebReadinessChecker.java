package com.huanjing.geo.module.presale.generate.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.UsageScene;
import com.huanjing.geo.module.presale.generate.PresalePlatformConfigQueries;
import com.huanjing.geo.module.presale.generate.web.provider.PresaleWebProvider;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PresaleWebReadinessChecker {
    private static final Set<String> SENSITIVE_PROVIDER_FIELDS = Set.of(
            "apikey", "secret", "secretkey", "accesskey", "password",
            "credential", "credentials", "authorization", "bearertoken", "token");
    private final AiPlatformConfigMapper platformConfigMapper;
    private final PlatformCredentialService credentialService;
    private final ObjectMapper objectMapper;
    private final PresaleWebQueryProperties properties;
    private final List<WebSearchCodec> codecs;
    private final List<PresaleWebProvider> providers;

    public PresaleQueryWebMode configuredMode() {
        return properties.getMode() == null ? PresaleQueryWebMode.OFF : properties.getMode();
    }

    /** Used before create/regenerate. It performs no write and therefore must precede any state mutation. */
    public PresaleWebExecutionContext checkConfiguredMode() {
        return check(configuredMode());
    }

    /** Used by retry and the defensive orchestrator preflight with the mode already saved on the version. */
    public PresaleWebExecutionContext checkSavedMode(String savedMode) {
        try {
            return check(PresaleQueryWebMode.from(savedMode));
        } catch (IllegalArgumentException ex) {
            throw new PresaleWebReadinessException("Invalid report query_web_mode: " + savedMode);
        }
    }

    public PresaleWebExecutionContext check(PresaleQueryWebMode mode) {
        PresaleQueryWebMode fixedMode = mode == null ? PresaleQueryWebMode.OFF : mode;
        if (!fixedMode.requiresWebQuery()) {
            return new PresaleWebExecutionContext(fixedMode, Map.of());
        }
        List<AiPlatformConfig> bases = platformConfigMapper.selectList(
                PresalePlatformConfigQueries.requiredReportPlatformWrapper());
        if (bases == null || bases.isEmpty()) {
            throw new PresaleWebReadinessException(
                    "REQUIRED web QUERY has no enabled presale platform or web companion");
        }

        EnumSet<IntegrationType> registeredCodecs = EnumSet.noneOf(IntegrationType.class);
        codecs.forEach(codec -> registeredCodecs.add(codec.integrationType()));
        Map<IntegrationType, PresaleWebProvider> registeredProviders = new LinkedHashMap<>();
        for (PresaleWebProvider provider : providers) {
            if (registeredProviders.put(provider.integrationType(), provider) != null) {
                throw new PresaleWebReadinessException(
                        "Duplicate presale web provider for " + provider.integrationType());
            }
        }
        Map<String, ResolvedCompanionExecutionConfig> resolved = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        for (AiPlatformConfig base : bases) {
            try {
                ResolvedCompanionExecutionConfig config = resolveOptional(
                        base, registeredCodecs, registeredProviders);
                if (config != null) {
                    resolved.put(base.getPlatformCode(), config);
                }
            } catch (RuntimeException ex) {
                failures.add(base.getPlatformCode() + ": " + ex.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new PresaleWebReadinessException(
                    "REQUIRED configured web companion readiness failed: " + String.join("; ", failures));
        }
        return new PresaleWebExecutionContext(fixedMode, resolved, bases);
    }

    private ResolvedCompanionExecutionConfig resolveOptional(AiPlatformConfig base,
                                                              Set<IntegrationType> registeredCodecs,
                                                              Map<IntegrationType, PresaleWebProvider> registeredProviders) {
        if (!StringUtils.hasText(base.getChannelCode())) {
            throw new IllegalArgumentException("base channel_code is blank");
        }
        List<AiPlatformConfig> rows = platformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getChannelCode, base.getChannelCode())
                        .eq(AiPlatformConfig::getUsageScene, UsageScene.QUESTION_POLL_WEB.name())
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForPresale, true));
        rows = rows == null ? List.of() : rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getEnabled()))
                .filter(row -> Boolean.TRUE.equals(row.getEnabledForPresale()))
                .filter(row -> parseIntegrationType(row.getIntegrationType()).isWebSearch())
                .toList();
        if (rows == null || rows.isEmpty()) {
            if (!nativePresaleEnabled(base)) {
                throw new IllegalArgumentException(
                        "enabled web companion cannot be resolved for companion-only report platform");
            }
            // 基础模型仍可单独通过原生 API 参与；存在 companion 时由 companion 优先覆盖 QUERY。
            return null;
        }
        if (rows.size() != 1) {
            throw new IllegalArgumentException("expected at most one enabled web companion, found "
                    + rows.size());
        }
        AiPlatformConfig companion = rows.get(0);
        if (Boolean.TRUE.equals(companion.getDegraded())) {
            throw new IllegalArgumentException("companion is degraded");
        }
        if (!"normal".equalsIgnoreCase(companion.getCurrentHealthStatus())) {
            throw new IllegalArgumentException("companion health is not normal");
        }
        IntegrationType integrationType = parseIntegrationType(companion.getIntegrationType());
        PresaleWebProvider webProvider = registeredProviders.get(integrationType);
        if (!integrationType.isWebSearch() || webProvider == null) {
            throw new IllegalArgumentException("no active presale web provider for " + integrationType);
        }
        if (webProvider.requiresCodec() && !registeredCodecs.contains(integrationType)) {
            throw new IllegalArgumentException("no active web codec for " + integrationType);
        }
        PresaleWebEndpointPolicy.validate(integrationType, companion.getApiUrl());
        if (!StringUtils.hasText(companion.getModelId())) {
            throw new IllegalArgumentException("companion model_id is blank");
        }
        JsonNode providerConfig = parseProviderConfig(companion.getProviderConfigJson());
        rejectEmbeddedCredentials(providerConfig);
        String provider = providerConfig.path("provider").asText(null);
        if (!StringUtils.hasText(provider)) {
            throw new IllegalArgumentException("provider_config_json.provider is blank");
        }
        String credential = credentialService.resolvePrimaryCredentialStrict(
                companion.getPrimaryKeyRef(), companion.getApiKey());
        if (!StringUtils.hasText(credential)) {
            throw new IllegalArgumentException("companion credential cannot be resolved");
        }
        String credentialRef = StringUtils.hasText(companion.getPrimaryKeyRef())
                ? companion.getPrimaryKeyRef().trim()
                : PlatformCredentialService.databaseCredentialRef(companion.getId());
        return new ResolvedCompanionExecutionConfig(
                base.getPlatformCode(), base.getPlatformName(), companion.getId(),
                companion.getPlatformCode(),
                defaultText(companion.getPlatformName(), companion.getPlatformCode()),
                valueOrZero(companion.getConfigVersion()),
                companion.getChannelCode(), provider.trim(), integrationType,
                companion.getApiUrl().trim(), companion.getModelId().trim(),
                defaultText(companion.getModelName(), companion.getModelId()), credentialRef,
                providerConfig.toString(),
                Math.max(1_000, properties.getConnectTimeoutMs()),
                positiveOrDefault(companion.getTimeoutMs(), properties.getRequestTimeoutMs()),
                positiveOrDefault(companion.getConcurrencyLimit(), 1),
                positiveOrDefault(companion.getRpmLimit(), 60),
                positiveOrDefault(companion.getTpmLimit(), 60_000));
    }

    private boolean nativePresaleEnabled(AiPlatformConfig base) {
        return base != null
                && Boolean.TRUE.equals(base.getEnabled())
                && Boolean.TRUE.equals(base.getEnabledForPresale())
                && StringUtils.hasText(base.getLowModelId());
    }

    private JsonNode parseProviderConfig(String json) {
        try {
            JsonNode node = objectMapper.readTree(StringUtils.hasText(json) ? json : "{}");
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("provider_config_json must be an object");
            }
            return node;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("provider_config_json is invalid");
        }
    }

    private void rejectEmbeddedCredentials(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String normalized = entry.getKey().replaceAll("[^A-Za-z0-9]", "")
                        .toLowerCase(Locale.ROOT);
                if (SENSITIVE_PROVIDER_FIELDS.contains(normalized)) {
                    throw new IllegalArgumentException(
                            "provider_config_json must not contain embedded credentials");
                }
                rejectEmbeddedCredentials(entry.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::rejectEmbeddedCredentials);
        }
    }

    private IntegrationType parseIntegrationType(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("integration_type is blank");
        }
        try {
            return IntegrationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unsupported integration_type: " + value);
        }
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value < 1 ? Math.max(1, defaultValue) : value;
    }

    private Long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
