package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.LlmRoutingStrategy;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionPollPlatformPreflightService {

    public static final String PREFLIGHT_FAILED = "preflight_failed";

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final LlmCallFacade llmCallFacade;
    private final DispatchProperties dispatchProperties;

    public void runPreflight() {
        if (!dispatchProperties.isQuestionPollPreflightEnabled()) {
            return;
        }
        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForQuestionPoll, true)
                        .eq(AiPlatformConfig::getUsageScene, "STANDARD_CHAT")
                        .orderByAsc(AiPlatformConfig::getPriorityLevel, AiPlatformConfig::getId)
        );
        for (AiPlatformConfig platform : platforms) {
            preflight(platform);
        }
    }

    private void preflight(AiPlatformConfig platform) {
        try {
            llmCallFacade.execute(LlmCallRequest.routed(new LlmRouteRequest(
                    LlmFeature.MONITORING,
                    "You are a service health probe.",
                    "Reply with OK only.",
                    0D,
                    dispatchProperties.getModelConnectTimeoutMs(),
                    Math.min(dispatchProperties.getModelRequestTimeoutMs(), 15_000),
                    LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS,
                    0,
                    16,
                    false,
                    1,
                    0,
                    List.of(platform),
                    false,
                    LlmRoutingStrategy.PINNED
            )));
            log.info("Question-poll platform preflight passed, platform={}", platform.getPlatformCode());
        } catch (Exception ex) {
            aiPlatformConfigMapper.update(null, new LambdaUpdateWrapper<AiPlatformConfig>()
                    .eq(AiPlatformConfig::getId, platform.getId())
                    .set(AiPlatformConfig::getCurrentHealthStatus, PREFLIGHT_FAILED)
                    .set(AiPlatformConfig::getLastFailureAt, LocalDateTime.now()));
            log.warn("Question-poll platform preflight failed, platform={}, reason={}",
                    platform.getPlatformCode(), ex.getMessage());
        }
    }
}
