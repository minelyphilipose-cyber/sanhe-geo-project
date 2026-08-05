package com.huanjing.geo.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.image.CompressedImage;
import com.huanjing.geo.common.image.ImageCompressionService;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardAiPlatformCatalog;
import com.huanjing.geo.module.system.dto.AiPlatformConfigCreateRequest;
import com.huanjing.geo.module.system.dto.AiPlatformConfigUpdateRequest;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.UsageScene;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiPlatformConfigService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_\\-]{1,63}$");
    private static final long MAX_LOGO_FILE_SIZE = 10 * 1024 * 1024L;
    private static final Set<String> PRIORITY_SET = Set.of("P0", "P1", "P2");
    private static final Set<String> PRESALE_EVALUATE_PLATFORM_CODES = Set.of(
            "deepseek", "doubao", "qwen", "mimo", "zhipu"
    );

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;
    private final PlatformCredentialService platformCredentialService;
    private final MinioStorageService minioStorageService;
    private final ImageCompressionService imageCompressionService;
    private final ObjectMapper objectMapper;
    private final MobileDashboardAiPlatformCatalog mobileDashboardAiPlatformCatalog;

    public Page<AiPlatformConfig> page(long current, long size, String keyword, String priorityLevel, Boolean enabled) {
        currentUserService.ensureUserManageOperator();
        LambdaQueryWrapper<AiPlatformConfig> wrapper = new LambdaQueryWrapper<AiPlatformConfig>()
                .orderByAsc(AiPlatformConfig::getPriorityLevel)
                .orderByAsc(AiPlatformConfig::getPlatformName)
                .orderByAsc(AiPlatformConfig::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AiPlatformConfig::getPlatformCode, keyword)
                    .or().like(AiPlatformConfig::getPlatformName, keyword)
                    .or().like(AiPlatformConfig::getModelName, keyword));
        }
        if (StringUtils.hasText(priorityLevel)) {
            wrapper.eq(AiPlatformConfig::getPriorityLevel, priorityLevel.trim());
        }
        if (enabled != null) {
            wrapper.eq(AiPlatformConfig::getEnabled, enabled);
        }
        return aiPlatformConfigMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public AiPlatformConfig create(AiPlatformConfigCreateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("user.manage");
        validateRequest(
                req.getPlatformCode(),
                normalize(req.getChannelCode(), req.getPlatformCode()),
                normalize(req.getUsageScene(), UsageScene.STANDARD_CHAT.name()),
                normalize(req.getIntegrationType(), IntegrationType.OPENAI_CHAT.name()),
                req.getProviderConfig(),
                req.getPlatformName(),
                req.getPriorityLevel(),
                req.getRpmLimit(),
                req.getTpmLimit(),
                req.getApiKey(),
                req.getPrimaryKeyRef(),
                req.getApiUrl(),
                req.getModelId(),
                req.getLowModelId(),
                req.getModelName(),
                req.getConcurrencyLimit(),
                req.getEnabled(),
                req.getEnabledForPresale(),
                req.getPresaleEvaluateEnabled(),
                req.getEnabledForGeoQuestion(),
                req.getEnabledForQuestionPoll(),
                req.getEnabledForMobileDashboard(),
                req.getDegraded(),
                req.getDegradedReason()
        );
        ensureUniqueCode(req.getPlatformCode(), null);
        ensureUniqueChannelScene(
                normalize(req.getChannelCode(), req.getPlatformCode()),
                normalize(req.getUsageScene(), UsageScene.STANDARD_CHAT.name()),
                null
        );

        AiPlatformConfig entity = new AiPlatformConfig();
        fillEntity(entity, req);
        aiPlatformConfigMapper.insert(entity);

        activityLogService.logAction(
                operator.getId(),
                "platform.create",
                "platform",
                entity.getId(),
                null,
                snapshot(entity),
                null
        );
        refreshMobileDashboardCatalogAfterCommit();
        return entity;
    }

    @Transactional
    public AiPlatformConfig update(Long id, AiPlatformConfigUpdateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("user.manage");
        AiPlatformConfig entity = requireById(id);
        String channelCode = normalize(req.getChannelCode(), entity.getChannelCode());
        String usageScene = normalize(req.getUsageScene(), entity.getUsageScene());
        String integrationType = normalize(req.getIntegrationType(), entity.getIntegrationType());
        JsonNode providerConfig = req.getProviderConfig() != null
                ? req.getProviderConfig()
                : readJson(entity.getProviderConfigJson());
        String effectiveApiKey = Boolean.TRUE.equals(req.getClearApiKey())
                ? null
                : (StringUtils.hasText(req.getApiKey()) ? req.getApiKey() : entity.getApiKey());
        String effectivePrimaryKeyRef = Boolean.TRUE.equals(req.getClearPrimaryKeyRef())
                ? null
                : (StringUtils.hasText(req.getPrimaryKeyRef())
                        ? req.getPrimaryKeyRef() : entity.getPrimaryKeyRef());
        validateRequest(
                req.getPlatformCode(),
                channelCode,
                usageScene,
                integrationType,
                providerConfig,
                req.getPlatformName(),
                req.getPriorityLevel(),
                req.getRpmLimit(),
                req.getTpmLimit(),
                effectiveApiKey,
                effectivePrimaryKeyRef,
                req.getApiUrl(),
                req.getModelId(),
                req.getLowModelId(),
                req.getModelName(),
                req.getConcurrencyLimit(),
                req.getEnabled(),
                req.getEnabledForPresale(),
                req.getPresaleEvaluateEnabled(),
                req.getEnabledForGeoQuestion(),
                req.getEnabledForQuestionPoll(),
                req.getEnabledForMobileDashboard(),
                req.getDegraded(),
                req.getDegradedReason()
        );
        ensureUniqueCode(req.getPlatformCode(), id);
        ensureUniqueChannelScene(channelCode, usageScene, id);

        Map<String, Object> before = snapshot(entity);

        fillEntity(entity, req);
        aiPlatformConfigMapper.updateById(entity);
        // MyBatis-Plus omits null fields in updateById. Persist both credential columns
        // explicitly so switching sources also removes the previous source atomically.
        aiPlatformConfigMapper.updateCredentialSources(
                entity.getId(), entity.getApiKey(), entity.getPrimaryKeyRef());

        activityLogService.logAction(
                operator.getId(),
                "platform.update",
                "platform",
                entity.getId(),
                before,
                snapshot(entity),
                null
        );
        refreshMobileDashboardCatalogAfterCommit();
        return entity;
    }

    public AiPlatformConfig updatePresaleEnabled(Long id, Boolean enabledForPresale) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("user.manage");

        AiPlatformConfig entity = requireById(id);
        if (Boolean.TRUE.equals(enabledForPresale) && !StringUtils.hasText(entity.getLowModelId())) {
            throw new BizException(400, "low_model_id is required when enabling presale");
        }

        Map<String, Object> before = snapshot(entity);
        entity.setEnabledForPresale(Boolean.TRUE.equals(enabledForPresale));
        if (!Boolean.TRUE.equals(enabledForPresale)) {
            entity.setPresaleEvaluateEnabled(false);
        }
        aiPlatformConfigMapper.updateById(entity);

        activityLogService.logAction(
                operator.getId(),
                "platform.presale_enabled.update",
                "platform",
                entity.getId(),
                before,
                snapshot(entity),
                null
        );
        return entity;
    }

    public AiPlatformConfig uploadLogo(Long id, MultipartFile file) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("user.manage");
        validateLogoFile(file);

        AiPlatformConfig entity = requireById(id);
        Map<String, Object> before = snapshot(entity);
        CompressedImage image = imageCompressionService.compressToLimit(file);
        String objectKey = buildLogoObjectKey(entity.getId(), image.fileName());
        String logoUrl = minioStorageService.uploadBytes(image.bytes(), objectKey, image.contentType());

        entity.setPlatformLogoObjectKey(objectKey);
        entity.setPlatformLogoUrl(logoUrl);
        aiPlatformConfigMapper.updateById(entity);
        activityLogService.logAction(
                operator.getId(),
                "platform.logo.update",
                "platform",
                entity.getId(),
                before,
                snapshot(entity),
                null
        );
        return entity;
    }

    public PlatformLogoResource loadLogoResource(Long id) {
        AiPlatformConfig entity = requireById(id);
        String objectKey = resolveLogoObjectKey(entity);
        byte[] bytes = minioStorageService.getObjectBytes(objectKey);
        return new PlatformLogoResource(bytes, contentTypeByObjectKey(objectKey));
    }

    public void delete(Long id) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("user.manage");
        AiPlatformConfig entity = requireById(id);
        aiPlatformConfigMapper.deleteById(id);
        activityLogService.logAction(
                operator.getId(),
                "platform.delete",
                "platform",
                id,
                snapshot(entity),
                null,
                null
        );
        refreshMobileDashboardCatalogAfterCommit();
    }

    private AiPlatformConfig requireById(Long id) {
        AiPlatformConfig entity = aiPlatformConfigMapper.selectById(id);
        if (entity == null) {
            throw new BizException(404, "Platform config not found");
        }
        return entity;
    }

    private void validateRequest(
            String platformCode,
            String channelCode,
            String usageScene,
            String integrationType,
            JsonNode providerConfig,
            String platformName,
            String priorityLevel,
            Integer rpmLimit,
            Integer tpmLimit,
            String apiKey,
            String primaryKeyRef,
            String apiUrl,
            String modelId,
            String lowModelId,
            String modelName,
            Integer concurrencyLimit,
            Boolean enabled,
            Boolean enabledForPresale,
            Boolean presaleEvaluateEnabled,
            Boolean enabledForGeoQuestion,
            Boolean enabledForQuestionPoll,
            Boolean enabledForMobileDashboard,
            Boolean degraded,
            String degradedReason
    ) {
        if (!StringUtils.hasText(platformCode) || !CODE_PATTERN.matcher(platformCode.trim()).matches()) {
            throw new BizException(400, "Invalid platform_code");
        }
        if (!StringUtils.hasText(channelCode) || !CODE_PATTERN.matcher(channelCode.trim()).matches()) {
            throw new BizException(400, "Invalid channel_code");
        }
        UsageScene scene = parseEnum(UsageScene.class, usageScene, "usage_scene");
        IntegrationType integration = parseEnum(IntegrationType.class, integrationType, "integration_type");
        if (!StringUtils.hasText(platformName)) {
            throw new BizException(400, "platform_name is required");
        }
        if (!StringUtils.hasText(priorityLevel) || !PRIORITY_SET.contains(priorityLevel.trim())) {
            throw new BizException(400, "priority_level must be P0/P1/P2");
        }
        validateCredentialSource(scene, integration, apiKey, primaryKeyRef, providerConfig);
        if (rpmLimit != null && rpmLimit <= 0) {
            throw new BizException(400, "rpm_limit must be > 0");
        }
        if (tpmLimit != null && tpmLimit <= 0) {
            throw new BizException(400, "tpm_limit must be > 0");
        }
        if (!StringUtils.hasText(apiUrl)) {
            throw new BizException(400, "api_url is required");
        }
        if (integration.isWebSearch()) {
            validateFinalHttpsEndpoint(apiUrl, "api_url");
        }
        if (!StringUtils.hasText(modelId)) {
            throw new BizException(400, "model_id is required");
        }
        if (!StringUtils.hasText(modelName)) {
            throw new BizException(400, "model_name is required");
        }
        if (concurrencyLimit != null && concurrencyLimit <= 0) {
            throw new BizException(400, "concurrency_limit must be > 0");
        }
        if (Boolean.TRUE.equals(presaleEvaluateEnabled)) {
            String normalizedCode = platformCode.trim();
            if (!PRESALE_EVALUATE_PLATFORM_CODES.contains(normalizedCode)) {
                throw new BizException(400, "presale evaluation model must be one of deepseek/doubao/qwen/mimo/zhipu");
            }
            if (!Boolean.TRUE.equals(enabled)) {
                throw new BizException(400, "platform must be enabled when enabling presale evaluation");
            }
            if (!Boolean.TRUE.equals(enabledForPresale)) {
                throw new BizException(400, "presale must be enabled when enabling presale evaluation");
            }
            if (!StringUtils.hasText(lowModelId)) {
                throw new BizException(400, "low_model_id is required when enabling presale evaluation");
            }
        }
        if (Boolean.TRUE.equals(enabledForGeoQuestion)) {
            if (!Boolean.TRUE.equals(enabled)) {
                throw new BizException(400, "platform must be enabled when enabling GEO question generation");
            }
        }
        if (Boolean.TRUE.equals(enabledForQuestionPoll) && !Boolean.TRUE.equals(enabled)) {
            throw new BizException(400, "platform must be enabled when enabling question poll");
        }
        if (Boolean.TRUE.equals(enabledForMobileDashboard) && scene != UsageScene.QUESTION_POLL_WEB) {
            throw new BizException(400,
                    "mobile dashboard visibility requires usage_scene=QUESTION_POLL_WEB");
        }
        if (Boolean.TRUE.equals(degraded) && !StringUtils.hasText(degradedReason)) {
            throw new BizException(400, "degraded_reason is required when degraded=true");
        }
    }

    private void ensureUniqueCode(String platformCode, Long excludeId) {
        String code = platformCode.trim();
        AiPlatformConfig exists = aiPlatformConfigMapper.selectOne(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getPlatformCode, code)
                        .ne(excludeId != null, AiPlatformConfig::getId, excludeId)
        );
        if (exists != null) {
            throw new BizException(400, "platform_code already exists");
        }
    }

    private void ensureUniqueChannelScene(String channelCode, String usageScene, Long excludeId) {
        AiPlatformConfig exists = aiPlatformConfigMapper.selectOne(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getChannelCode, channelCode.trim())
                        .eq(AiPlatformConfig::getUsageScene, usageScene.trim())
                        .ne(excludeId != null, AiPlatformConfig::getId, excludeId)
                        .last("LIMIT 1")
        );
        if (exists != null) {
            throw new BizException(400, "channel_code and usage_scene already exist");
        }
    }

    private void validateCredentialSource(UsageScene scene,
                                          IntegrationType integration,
                                          String apiKey,
                                          String primaryKeyRef,
                                          JsonNode providerConfig) {
        if (!integration.isWebSearch()) {
            if (!StringUtils.hasText(apiKey) && !StringUtils.hasText(primaryKeyRef)) {
                throw new BizException(400, "api_key or primary_key_ref is required");
            }
            return;
        }
        if (scene != UsageScene.QUESTION_POLL_WEB) {
            throw new BizException(400, "web-search integration requires usage_scene=QUESTION_POLL_WEB");
        }
        boolean hasApiKey = StringUtils.hasText(apiKey);
        boolean hasPrimaryRef = StringUtils.hasText(primaryKeyRef);
        if (hasApiKey == hasPrimaryRef) {
            throw new BizException(400,
                    "web-search configuration requires exactly one of api_key or primary_key_ref");
        }
        if (hasPrimaryRef) {
            validateCredentialRef(primaryKeyRef, "primary_key_ref");
        }
        if (providerConfig != null && (providerConfig.hasNonNull("credentialRef")
                || providerConfig.hasNonNull("searchCredentialRef")
                || providerConfig.hasNonNull("generationCredentialRef"))) {
            throw new BizException(400, "single-provider web credentials must use primary_key_ref only");
        }
    }

    private void requireEnvRef(JsonNode providerConfig, String fieldName) {
        JsonNode value = requireText(providerConfig, fieldName);
        validateEnvRef(value.asText(), "provider_config." + fieldName);
    }

    private JsonNode requireText(JsonNode providerConfig, String fieldName) {
        if (providerConfig == null || !providerConfig.isObject()) {
            throw new BizException(400, "provider_config must be a JSON object");
        }
        JsonNode value = providerConfig.get(fieldName);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw new BizException(400, "provider_config." + fieldName + " is required");
        }
        return value;
    }

    private void validateEnvRef(String value, String fieldName) {
        if (!StringUtils.hasText(value) || !value.trim().matches("^env://[A-Za-z_][A-Za-z0-9_]*$")) {
            throw new BizException(400, fieldName + " must use env://ENVIRONMENT_VARIABLE");
        }
    }

    private void validateCredentialRef(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.matches("^env://[A-Za-z_][A-Za-z0-9_]*$")
                || normalized.matches("^db://ai-platform-config/[1-9][0-9]*$")) {
            return;
        }
        throw new BizException(400,
                fieldName + " must use env://ENVIRONMENT_VARIABLE or db://ai-platform-config/{id}");
    }

    private void validateFinalHttpsEndpoint(String value, String fieldName) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("HTTPS endpoint required");
            }
        } catch (IllegalArgumentException ex) {
            throw new BizException(400, fieldName + " must be a complete HTTPS request URL");
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String fieldName) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new BizException(400, "Invalid " + fieldName + ": " + value);
        }
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback.trim();
    }

    private String writeJson(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException("Failed to serialize provider_config", ex);
        }
    }

    private JsonNode readJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new BizException("Stored provider_config is invalid JSON", ex);
        }
    }

    private void fillEntity(AiPlatformConfig entity, AiPlatformConfigCreateRequest req) {
        entity.setPlatformCode(req.getPlatformCode().trim());
        entity.setChannelCode(normalize(req.getChannelCode(), req.getPlatformCode()));
        entity.setUsageScene(normalize(req.getUsageScene(), UsageScene.STANDARD_CHAT.name()));
        entity.setIntegrationType(normalize(req.getIntegrationType(), IntegrationType.OPENAI_CHAT.name()));
        entity.setProviderConfigJson(writeJson(req.getProviderConfig()));
        entity.setConfigVersion(1L);
        entity.setPlatformName(req.getPlatformName().trim());
        entity.setPlatformHomeUrl(StringUtils.hasText(req.getPlatformHomeUrl()) ? req.getPlatformHomeUrl().trim() : null);
        entity.setPlatformLogoUrl(StringUtils.hasText(req.getPlatformLogoUrl()) ? req.getPlatformLogoUrl().trim() : null);
        entity.setPlatformLogoObjectKey(null);
        entity.setPriorityLevel(req.getPriorityLevel().trim());
        entity.setRpmLimit(req.getRpmLimit() != null ? req.getRpmLimit() : 60);
        entity.setTpmLimit(req.getTpmLimit() != null ? req.getTpmLimit() : 60000);
        entity.setApiKey(platformCredentialService.encryptForStorage(req.getApiKey()));
        entity.setPrimaryKeyRef(StringUtils.hasText(req.getPrimaryKeyRef()) ? req.getPrimaryKeyRef().trim() : null);
        entity.setBackupKeyRef(StringUtils.hasText(req.getBackupKeyRef()) ? req.getBackupKeyRef().trim() : null);
        entity.setBackupProviderName(StringUtils.hasText(req.getBackupProviderName()) ? req.getBackupProviderName().trim() : null);
        entity.setBackupApiUrl(StringUtils.hasText(req.getBackupApiUrl()) ? req.getBackupApiUrl().trim() : null);
        entity.setBackupModelId(StringUtils.hasText(req.getBackupModelId()) ? req.getBackupModelId().trim() : null);
        entity.setApiUrl(req.getApiUrl().trim());
        entity.setModelId(req.getModelId().trim());
        entity.setLowModelId(StringUtils.hasText(req.getLowModelId()) ? req.getLowModelId().trim() : null);
        entity.setModelName(req.getModelName().trim());
        entity.setConcurrencyLimit(req.getConcurrencyLimit() != null ? req.getConcurrencyLimit() : 1);
        entity.setEnabled(req.getEnabled());
        entity.setEnabledForPresale(req.getEnabledForPresale() != null ? req.getEnabledForPresale() : true);
        entity.setPresaleEvaluateEnabled(Boolean.TRUE.equals(req.getPresaleEvaluateEnabled()));
        entity.setEnabledForArticle(req.getEnabledForArticle() != null ? req.getEnabledForArticle() : false);
        entity.setEnabledForGeoQuestion(Boolean.TRUE.equals(req.getEnabledForGeoQuestion()));
        entity.setEnabledForQuestionPoll(Boolean.TRUE.equals(req.getEnabledForQuestionPoll()));
        entity.setEnabledForMobileDashboard(Boolean.TRUE.equals(req.getEnabledForMobileDashboard()));
        entity.setMaxRetry(req.getMaxRetry() != null ? req.getMaxRetry() : 2);
        entity.setTimeoutMs(req.getTimeoutMs() != null ? req.getTimeoutMs() : 60000);
        entity.setRateLimitQps(req.getRateLimitQps() != null ? req.getRateLimitQps() : 3);
        entity.setDegraded(req.getDegraded());
        entity.setDegradedReason(StringUtils.hasText(req.getDegradedReason()) ? req.getDegradedReason().trim() : null);
        entity.setCurrentHealthStatus("normal");
        entity.setRemark(req.getRemark());
    }

    private void fillEntity(AiPlatformConfig entity, AiPlatformConfigUpdateRequest req) {
        entity.setPlatformCode(req.getPlatformCode().trim());
        entity.setChannelCode(normalize(req.getChannelCode(), entity.getChannelCode()));
        entity.setUsageScene(normalize(req.getUsageScene(), entity.getUsageScene()));
        entity.setIntegrationType(normalize(req.getIntegrationType(), entity.getIntegrationType()));
        if (req.getProviderConfig() != null) {
            entity.setProviderConfigJson(writeJson(req.getProviderConfig()));
        }
        entity.setConfigVersion((entity.getConfigVersion() == null ? 0L : entity.getConfigVersion()) + 1L);
        entity.setPlatformName(req.getPlatformName().trim());
        entity.setPlatformHomeUrl(StringUtils.hasText(req.getPlatformHomeUrl()) ? req.getPlatformHomeUrl().trim() : null);
        entity.setPlatformLogoUrl(StringUtils.hasText(req.getPlatformLogoUrl()) ? req.getPlatformLogoUrl().trim() : null);
        if (!StringUtils.hasText(req.getPlatformLogoUrl())) {
            entity.setPlatformLogoObjectKey(null);
        }
        entity.setPriorityLevel(req.getPriorityLevel().trim());
        entity.setRpmLimit(req.getRpmLimit() != null ? req.getRpmLimit() : entity.getRpmLimit());
        entity.setTpmLimit(req.getTpmLimit() != null ? req.getTpmLimit() : entity.getTpmLimit());
        if (Boolean.TRUE.equals(req.getClearApiKey())) {
            entity.setApiKey(null);
        } else if (StringUtils.hasText(req.getApiKey())) {
            entity.setApiKey(platformCredentialService.encryptForStorage(req.getApiKey()));
        }
        if (Boolean.TRUE.equals(req.getClearPrimaryKeyRef())) {
            entity.setPrimaryKeyRef(null);
        } else if (StringUtils.hasText(req.getPrimaryKeyRef())) {
            entity.setPrimaryKeyRef(req.getPrimaryKeyRef().trim());
        }
        entity.setBackupKeyRef(StringUtils.hasText(req.getBackupKeyRef()) ? req.getBackupKeyRef().trim() : null);
        entity.setBackupProviderName(StringUtils.hasText(req.getBackupProviderName()) ? req.getBackupProviderName().trim() : null);
        entity.setBackupApiUrl(StringUtils.hasText(req.getBackupApiUrl()) ? req.getBackupApiUrl().trim() : null);
        entity.setBackupModelId(StringUtils.hasText(req.getBackupModelId()) ? req.getBackupModelId().trim() : null);
        entity.setApiUrl(req.getApiUrl().trim());
        entity.setModelId(req.getModelId().trim());
        entity.setLowModelId(StringUtils.hasText(req.getLowModelId()) ? req.getLowModelId().trim() : null);
        entity.setModelName(req.getModelName().trim());
        entity.setConcurrencyLimit(req.getConcurrencyLimit() != null ? req.getConcurrencyLimit() : entity.getConcurrencyLimit());
        entity.setEnabled(req.getEnabled());
        entity.setEnabledForPresale(req.getEnabledForPresale() != null ? req.getEnabledForPresale() : entity.getEnabledForPresale());
        entity.setPresaleEvaluateEnabled(Boolean.TRUE.equals(req.getPresaleEvaluateEnabled()));
        entity.setEnabledForArticle(req.getEnabledForArticle() != null ? req.getEnabledForArticle() : entity.getEnabledForArticle());
        entity.setEnabledForGeoQuestion(req.getEnabledForGeoQuestion() != null ? req.getEnabledForGeoQuestion() : entity.getEnabledForGeoQuestion());
        entity.setEnabledForQuestionPoll(req.getEnabledForQuestionPoll() != null ? req.getEnabledForQuestionPoll() : entity.getEnabledForQuestionPoll());
        entity.setEnabledForMobileDashboard(req.getEnabledForMobileDashboard() != null
                ? req.getEnabledForMobileDashboard() : entity.getEnabledForMobileDashboard());
        entity.setMaxRetry(req.getMaxRetry() != null ? req.getMaxRetry() : entity.getMaxRetry());
        entity.setTimeoutMs(req.getTimeoutMs() != null ? req.getTimeoutMs() : entity.getTimeoutMs());
        entity.setRateLimitQps(req.getRateLimitQps() != null ? req.getRateLimitQps() : entity.getRateLimitQps());
        entity.setDegraded(req.getDegraded());
        entity.setDegradedReason(StringUtils.hasText(req.getDegradedReason()) ? req.getDegradedReason().trim() : null);
        entity.setRemark(req.getRemark());
    }

    private Map<String, Object> snapshot(AiPlatformConfig entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("platformCode", entity.getPlatformCode());
        snapshot.put("channelCode", entity.getChannelCode());
        snapshot.put("usageScene", entity.getUsageScene());
        snapshot.put("integrationType", entity.getIntegrationType());
        snapshot.put("providerConfigJson", entity.getProviderConfigJson());
        snapshot.put("configVersion", entity.getConfigVersion());
        snapshot.put("platformName", entity.getPlatformName());
        snapshot.put("platformHomeUrl", entity.getPlatformHomeUrl());
        snapshot.put("platformLogoUrl", entity.getPlatformLogoUrl());
        snapshot.put("platformLogoObjectKey", entity.getPlatformLogoObjectKey());
        snapshot.put("priorityLevel", entity.getPriorityLevel());
        snapshot.put("rpmLimit", entity.getRpmLimit());
        snapshot.put("tpmLimit", entity.getTpmLimit());
        snapshot.put("primaryKeyRef", entity.getPrimaryKeyRef());
        snapshot.put("backupKeyRef", entity.getBackupKeyRef());
        snapshot.put("backupProviderName", entity.getBackupProviderName());
        snapshot.put("apiUrl", entity.getApiUrl());
        snapshot.put("modelId", entity.getModelId());
        snapshot.put("lowModelId", entity.getLowModelId());
        snapshot.put("modelName", entity.getModelName());
        snapshot.put("concurrencyLimit", entity.getConcurrencyLimit());
        snapshot.put("enabled", entity.getEnabled());
        snapshot.put("enabledForPresale", entity.getEnabledForPresale());
        snapshot.put("presaleEvaluateEnabled", entity.getPresaleEvaluateEnabled());
        snapshot.put("enabledForArticle", entity.getEnabledForArticle());
        snapshot.put("enabledForGeoQuestion", entity.getEnabledForGeoQuestion());
        snapshot.put("enabledForQuestionPoll", entity.getEnabledForQuestionPoll());
        snapshot.put("enabledForMobileDashboard", entity.getEnabledForMobileDashboard());
        snapshot.put("maxRetry", entity.getMaxRetry());
        snapshot.put("timeoutMs", entity.getTimeoutMs());
        snapshot.put("rateLimitQps", entity.getRateLimitQps());
        snapshot.put("degraded", entity.getDegraded());
        snapshot.put("degradedReason", entity.getDegradedReason());
        snapshot.put("currentHealthStatus", entity.getCurrentHealthStatus());
        return snapshot;
    }

    private void refreshMobileDashboardCatalogAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            mobileDashboardAiPlatformCatalog.refresh();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mobileDashboardAiPlatformCatalog.refresh();
            }
        });
    }

    private void validateLogoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "Upload file is empty");
        }
        if (file.getSize() > MAX_LOGO_FILE_SIZE) {
            throw new BizException(400, "Upload file exceeds 10MB limit");
        }
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        boolean imageContentType = StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
        boolean imageExtension = hasImageExtension(fileName);
        if (!imageContentType && !imageExtension) {
            throw new BizException(400, "Platform logo must be an image file");
        }
    }

    private boolean hasImageExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".svg");
    }

    private String buildLogoObjectKey(Long platformId, String originalName) {
        String date = LocalDate.now().toString().replace("-", "");
        String random = UUID.randomUUID().toString().replace("-", "");
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > -1 && dot < originalName.length() - 1) {
            ext = originalName.substring(dot);
        }
        return "ai-platform/logo/" + platformId + "/" + date + "/" + random + ext;
    }

    private String extractLogoObjectKey(Long platformId, String logoUrl) {
        if (!StringUtils.hasText(logoUrl)) {
            throw new BizException(404, "Platform logo not found");
        }

        String raw = logoUrl.trim();
        String path = raw;
        try {
            java.net.URI uri = java.net.URI.create(raw);
            if (StringUtils.hasText(uri.getPath())) {
                path = uri.getPath();
            }
        } catch (IllegalArgumentException ignored) {
            // Legacy relative values are handled below.
        }

        String bucket = minioStorageService.bucketName();
        String bucketPrefix = "/" + bucket + "/";
        String ossBucketPrefix = "/oss" + bucketPrefix;
        String objectKey = null;
        if (path.startsWith(ossBucketPrefix)) {
            objectKey = path.substring(ossBucketPrefix.length());
        } else if (path.startsWith(bucketPrefix)) {
            objectKey = path.substring(bucketPrefix.length());
        } else if (path.startsWith(bucket + "/")) {
            objectKey = path.substring(bucket.length() + 1);
        } else if (path.startsWith("ai-platform/logo/")) {
            objectKey = path;
        }

        String expectedPrefix = "ai-platform/logo/" + platformId + "/";
        if (!StringUtils.hasText(objectKey) || !objectKey.startsWith(expectedPrefix)) {
            throw new BizException(404, "Platform logo not found");
        }
        return objectKey;
    }

    private String resolveLogoObjectKey(AiPlatformConfig entity) {
        String objectKey = entity.getPlatformLogoObjectKey();
        String expectedPrefix = "ai-platform/logo/" + entity.getId() + "/";
        if (StringUtils.hasText(objectKey) && objectKey.trim().startsWith(expectedPrefix)) {
            return objectKey.trim();
        }
        return extractLogoObjectKey(entity.getId(), entity.getPlatformLogoUrl());
    }

    private String contentTypeByObjectKey(String objectKey) {
        String normalized = objectKey == null ? "" : objectKey.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".svg")) return "image/svg+xml";
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".gif")) return "image/gif";
        if (normalized.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    public record PlatformLogoResource(byte[] bytes, String contentType) {
    }
}
