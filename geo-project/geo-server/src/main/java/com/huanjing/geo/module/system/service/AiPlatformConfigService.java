package com.huanjing.geo.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.dto.AiPlatformConfigCreateRequest;
import com.huanjing.geo.module.system.dto.AiPlatformConfigUpdateRequest;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiPlatformConfigService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_\\-]{1,63}$");
    private static final Set<String> PRIORITY_SET = Set.of("P0", "P1", "P2");
    private static final Set<String> PRESALE_EVALUATE_PLATFORM_CODES = Set.of(
            "deepseek", "doubao", "qwen", "mimo", "zhipu"
    );
    private static final Set<String> GEO_QUESTION_PLATFORM_CODES = Set.of("qwen", "deepseek", "mimo");

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;
    private final PlatformCredentialService platformCredentialService;

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
                req.getDegraded(),
                req.getDegradedReason()
        );
        ensureUniqueCode(req.getPlatformCode(), null);

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
        return entity;
    }

    public AiPlatformConfig update(Long id, AiPlatformConfigUpdateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("user.manage");
        validateRequest(
                req.getPlatformCode(),
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
                req.getDegraded(),
                req.getDegradedReason()
        );
        AiPlatformConfig entity = requireById(id);
        ensureUniqueCode(req.getPlatformCode(), id);

        Map<String, Object> before = snapshot(entity);

        fillEntity(entity, req);
        aiPlatformConfigMapper.updateById(entity);

        activityLogService.logAction(
                operator.getId(),
                "platform.update",
                "platform",
                entity.getId(),
                before,
                snapshot(entity),
                null
        );
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
            Boolean degraded,
            String degradedReason
    ) {
        if (!StringUtils.hasText(platformCode) || !CODE_PATTERN.matcher(platformCode.trim()).matches()) {
            throw new BizException(400, "Invalid platform_code");
        }
        if (!StringUtils.hasText(platformName)) {
            throw new BizException(400, "platform_name is required");
        }
        if (!StringUtils.hasText(priorityLevel) || !PRIORITY_SET.contains(priorityLevel.trim())) {
            throw new BizException(400, "priority_level must be P0/P1/P2");
        }
        if (!StringUtils.hasText(apiKey) && !StringUtils.hasText(primaryKeyRef)) {
            throw new BizException(400, "api_key or primary_key_ref is required");
        }
        if (rpmLimit != null && rpmLimit <= 0) {
            throw new BizException(400, "rpm_limit must be > 0");
        }
        if (tpmLimit != null && tpmLimit <= 0) {
            throw new BizException(400, "tpm_limit must be > 0");
        }
        if (!StringUtils.hasText(apiUrl)) {
            throw new BizException(400, "api_url is required");
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
            String normalizedCode = platformCode.trim();
            if (!GEO_QUESTION_PLATFORM_CODES.contains(normalizedCode)) {
                throw new BizException(400, "GEO question generation model must be one of qwen/deepseek/mimo");
            }
            if (!Boolean.TRUE.equals(enabled)) {
                throw new BizException(400, "platform must be enabled when enabling GEO question generation");
            }
            if (!StringUtils.hasText(lowModelId)) {
                throw new BizException(400, "low_model_id is required when enabling GEO question generation");
            }
        }
        if (Boolean.TRUE.equals(enabledForQuestionPoll) && !Boolean.TRUE.equals(enabled)) {
            throw new BizException(400, "platform must be enabled when enabling question poll");
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

    private void fillEntity(AiPlatformConfig entity, AiPlatformConfigCreateRequest req) {
        entity.setPlatformCode(req.getPlatformCode().trim());
        entity.setPlatformName(req.getPlatformName().trim());
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
        entity.setPlatformName(req.getPlatformName().trim());
        entity.setPriorityLevel(req.getPriorityLevel().trim());
        entity.setRpmLimit(req.getRpmLimit() != null ? req.getRpmLimit() : entity.getRpmLimit());
        entity.setTpmLimit(req.getTpmLimit() != null ? req.getTpmLimit() : entity.getTpmLimit());
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
        entity.setConcurrencyLimit(req.getConcurrencyLimit() != null ? req.getConcurrencyLimit() : entity.getConcurrencyLimit());
        entity.setEnabled(req.getEnabled());
        entity.setEnabledForPresale(req.getEnabledForPresale() != null ? req.getEnabledForPresale() : entity.getEnabledForPresale());
        entity.setPresaleEvaluateEnabled(Boolean.TRUE.equals(req.getPresaleEvaluateEnabled()));
        entity.setEnabledForArticle(req.getEnabledForArticle() != null ? req.getEnabledForArticle() : entity.getEnabledForArticle());
        entity.setEnabledForGeoQuestion(req.getEnabledForGeoQuestion() != null ? req.getEnabledForGeoQuestion() : entity.getEnabledForGeoQuestion());
        entity.setEnabledForQuestionPoll(req.getEnabledForQuestionPoll() != null ? req.getEnabledForQuestionPoll() : entity.getEnabledForQuestionPoll());
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
        snapshot.put("platformName", entity.getPlatformName());
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
        snapshot.put("maxRetry", entity.getMaxRetry());
        snapshot.put("timeoutMs", entity.getTimeoutMs());
        snapshot.put("rateLimitQps", entity.getRateLimitQps());
        snapshot.put("degraded", entity.getDegraded());
        snapshot.put("degradedReason", entity.getDegradedReason());
        snapshot.put("currentHealthStatus", entity.getCurrentHealthStatus());
        return snapshot;
    }
}
