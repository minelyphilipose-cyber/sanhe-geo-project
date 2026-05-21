package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.common.llm.pool.LlmExecutionGateway;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class PresaleEvaluationModelRouter {

    private static final Set<String> ALLOWED_PLATFORM_CODES = Set.of(
            "deepseek", "doubao", "qwen", "mimo", "zhipu"
    );

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final LlmExecutionGateway executionGateway;

    public PresaleEvaluationModelRouter(AiPlatformConfigMapper aiPlatformConfigMapper,
                                        ObjectProvider<LlmExecutionGateway> executionGatewayProvider) {
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.executionGateway = executionGatewayProvider.getIfAvailable();
    }

    public List<PlatformCallContext> routeContexts(PlatformCallContext source) {
        return evaluationPlatforms().stream()
                .map(platform -> toContext(source, platform.getPlatformCode()))
                .toList();
    }

    public Optional<String> preferredPlatformCode() {
        return evaluationPlatforms().stream()
                .map(AiPlatformConfig::getPlatformCode)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    private PlatformCallContext toContext(PlatformCallContext source, String platformCode) {
        return new PlatformCallContext(
                source.versionId(),
                source.batchNo(),
                platformCode,
                source.promptTemplateId(),
                source.competitorName(),
                source.brandName(),
                source.operatorUserId(),
                source.operatorIsManager()
        );
    }

    private List<AiPlatformConfig> evaluationPlatforms() {
        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(
                PresalePlatformConfigQueries.presaleEnabledWrapper()
                        .eq(AiPlatformConfig::getPresaleEvaluateEnabled, true)
                        .in(AiPlatformConfig::getPlatformCode, ALLOWED_PLATFORM_CODES)
        );
        if (platforms == null || platforms.isEmpty()) {
            return List.of();
        }
        return platforms.stream()
                .filter(platform -> platform != null && StringUtils.hasText(platform.getPlatformCode()))
                .sorted(Comparator
                        .comparingDouble(this::activeRatio)
                        .thenComparing(platform -> priorityRank(platform.getPriorityLevel()))
                        .thenComparing(AiPlatformConfig::getPlatformCode)
                        .thenComparing(platform -> platform.getId() == null ? Long.MAX_VALUE : platform.getId()))
                .toList();
    }

    private double activeRatio(AiPlatformConfig platform) {
        int limit = platform.getConcurrencyLimit() == null || platform.getConcurrencyLimit() <= 0
                ? 1
                : platform.getConcurrencyLimit();
        Long active = executionGateway == null ? 0L : executionGateway.activePlatformCount(platform.getPlatformCode());
        return (active == null ? 0D : active.doubleValue()) / limit;
    }

    private int priorityRank(String priorityLevel) {
        if ("P0".equals(priorityLevel)) {
            return 0;
        }
        if ("P1".equals(priorityLevel)) {
            return 1;
        }
        if ("P2".equals(priorityLevel)) {
            return 2;
        }
        return 3;
    }
}
