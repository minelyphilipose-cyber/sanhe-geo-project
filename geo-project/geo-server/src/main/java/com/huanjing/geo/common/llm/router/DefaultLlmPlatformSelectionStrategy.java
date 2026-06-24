package com.huanjing.geo.common.llm.router;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.llm.LlmRoutingStrategy;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultLlmPlatformSelectionStrategy implements LlmPlatformSelectionStrategy {
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;

    @Override
    public List<LlmPlatformCandidate> selectCandidates(LlmRouteRequest request) {
        List<AiPlatformConfig> configs = request.platformConfigs().isEmpty()
                ? loadByFeature(request.feature())
                : request.platformConfigs();
        if (request.routingStrategy() == LlmRoutingStrategy.PINNED && configs.size() != 1) {
            throw new IllegalArgumentException("PINNED LLM routing requires exactly one platform config");
        }
        List<LlmPlatformCandidate> candidates = new ArrayList<>();
        for (AiPlatformConfig config : rotate(configs, request.cursor())) {
            addPrimary(candidates, config);
            if (request.routingStrategy() == LlmRoutingStrategy.PINNED
                    || request.routingStrategy() == LlmRoutingStrategy.CANDIDATE_LIST) {
                continue;
            }
            addBackupKey(candidates, config);
            addBackupProvider(candidates, config);
        }
        return candidates;
    }

    private List<AiPlatformConfig> loadByFeature(String feature) {
        LambdaQueryWrapper<AiPlatformConfig> wrapper = new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getEnabled, true);
        if (LlmFeature.ARTICLE.equals(feature)) {
            wrapper.eq(AiPlatformConfig::getEnabledForArticle, true);
        } else if (LlmFeature.PRESALE.equals(feature)) {
            wrapper.eq(AiPlatformConfig::getEnabledForPresale, true);
        }
        wrapper.orderByAsc(AiPlatformConfig::getId);
        return aiPlatformConfigMapper.selectList(wrapper);
    }

    private List<AiPlatformConfig> rotate(List<AiPlatformConfig> configs, int cursor) {
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }
        List<AiPlatformConfig> ordered = List.copyOf(configs);
        List<AiPlatformConfig> rotated = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            rotated.add(ordered.get(Math.floorMod(cursor + i, ordered.size())));
        }
        return rotated;
    }

    private void addPrimary(List<LlmPlatformCandidate> candidates, AiPlatformConfig config) {
        String apiKey = platformCredentialService.resolveApiKey(
                config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey());
        addCandidate(candidates, config, config.getPlatformCode(), config.getPlatformName(), "primary",
                config.getApiUrl(), config.getModelId(), config.getModelName(), apiKey);
    }

    private void addBackupKey(List<LlmPlatformCandidate> candidates, AiPlatformConfig config) {
        String apiKey = platformCredentialService.resolveApiKey(config.getPlatformCode(), config.getBackupKeyRef(), null);
        addCandidate(candidates, config, config.getPlatformCode(), config.getPlatformName(), "backup_key",
                config.getApiUrl(), config.getModelId(), config.getModelName(), apiKey);
    }

    private void addBackupProvider(List<LlmPlatformCandidate> candidates, AiPlatformConfig config) {
        if (!StringUtils.hasText(config.getBackupProviderName())) {
            return;
        }
        AiPlatformConfig backup = aiPlatformConfigMapper.selectOne(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getPlatformCode, config.getBackupProviderName().trim())
                        .eq(AiPlatformConfig::getEnabled, true)
                        .last("LIMIT 1")
        );
        if (backup == null) {
            return;
        }
        String apiUrl = StringUtils.hasText(config.getBackupApiUrl()) ? config.getBackupApiUrl().trim() : backup.getApiUrl();
        String modelId = StringUtils.hasText(config.getBackupModelId()) ? config.getBackupModelId().trim() : backup.getModelId();
        String apiKey = platformCredentialService.resolveApiKey(backup.getPlatformCode(), backup.getPrimaryKeyRef(), backup.getApiKey());
        addCandidate(candidates, backup, backup.getPlatformCode(), backup.getPlatformName(), "backup_provider",
                apiUrl, modelId, backup.getModelName(), apiKey);
    }

    private void addCandidate(List<LlmPlatformCandidate> candidates,
                              AiPlatformConfig config,
                              String platformCode,
                              String platformName,
                              String channel,
                              String apiUrl,
                              String modelId,
                              String modelName,
                              String apiKey) {
        if (!StringUtils.hasText(platformCode)
                || !StringUtils.hasText(apiUrl)
                || !StringUtils.hasText(modelId)
                || !StringUtils.hasText(apiKey)) {
            return;
        }
        candidates.add(new LlmPlatformCandidate(
                config,
                platformCode.trim(),
                platformName,
                channel,
                apiUrl.trim(),
                modelId.trim(),
                modelName,
                apiKey.trim()
        ));
    }
}
