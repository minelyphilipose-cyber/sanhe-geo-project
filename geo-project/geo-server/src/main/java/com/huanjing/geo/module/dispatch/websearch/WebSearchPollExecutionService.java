package com.huanjing.geo.module.dispatch.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.entity.PollInvocationAttempt;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.ResultCode;
import com.huanjing.geo.module.dispatch.websearch.enums.TriggerType;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchPlatformProfile;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchRequest;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderException;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class WebSearchPollExecutionService {

    public static final String ADAPTER_VERSION = "web-search-adapter-v1";
    private static final int MAX_SEARCH_ATTEMPTS = 2;
    private static final Duration DEADLINE_SAFETY_MARGIN = Duration.ofSeconds(5);
    private static final String SEARCH_RETRY_PROMPT = "\n必须先调用联网搜索工具核查，再基于本次搜索来源回答；不得只使用训练数据。";

    private final PollAttemptCreationService attemptCreationService;
    private final AttemptLifecycleService lifecycleService;
    private final PollResultProjectionService projectionService;
    private final WebSearchLlmGateway gateway;
    private final WebSearchAttemptResultWriter resultWriter;
    private final ObjectMapper objectMapper;

    public WebSearchPollExecutionOutcome execute(WebSearchPollCommand command) {
        validate(command);
        long chainStarted = System.currentTimeMillis();
        PollInvocationAttempt previous = null;
        WebSearchResponse lastResponse = null;
        ResultCode lastResultCode = ResultCode.R0;

        for (int attemptIndex = 0; attemptIndex < MAX_SEARCH_ATTEMPTS; attemptIndex++) {
            TriggerType triggerType = attemptIndex == 0 ? command.triggerType() : TriggerType.SEARCH_RETRY;
            String systemPrompt = attemptIndex == 0
                    ? command.systemPrompt()
                    : command.systemPrompt() + SEARCH_RETRY_PROMPT;
            PollInvocationAttempt attempt = createAttempt(command, previous, triggerType, systemPrompt);
            LocalDateTime startedAt = LocalDateTime.now();
            lifecycleService.start(attempt.getId(), startedAt);
            WebSearchResponse response;
            ResultCode resultCode;
            try {
                WebSearchRequest request = new WebSearchRequest(
                        attempt.getId(), command.projectId(), command.question(), systemPrompt,
                        profile(command.platform(), command.connectTimeoutMs(), command.requestTimeoutMs()),
                        attempt.getAttemptDeadlineAt()
                );
                response = gateway.execute(request);
                LocalDateTime completedAt = LocalDateTime.now();
                resultCode = resultWriter.writeSuccess(
                        attempt, response, command.brandNames(), completedAt);
            } catch (WebSearchProviderException ex) {
                finishFailure(attempt, ex.category(), providerErrorCode(ex), ex.getMessage());
                return WebSearchPollExecutionOutcome.failed(
                        attemptIndex + 1, elapsed(chainStarted), ex.category(), ex.getMessage());
            } catch (RuntimeException ex) {
                finishFailure(attempt, ErrorCategory.INTERNAL_ERROR, "INTERNAL_ERROR", ex.getMessage());
                return WebSearchPollExecutionOutcome.failed(
                        attemptIndex + 1, elapsed(chainStarted), ErrorCategory.INTERNAL_ERROR, ex.getMessage());
            }
            LocalDateTime completedAt = LocalDateTime.now();
            lifecycleService.succeed(attempt.getId(), completedAt);
            boolean shouldRetrySearch = resultCode == ResultCode.R1 && attemptIndex + 1 < MAX_SEARCH_ATTEMPTS;
            projectionService.finalizeAttempt(attempt.getId(), !shouldRetrySearch, completedAt);
            lastResponse = response;
            lastResultCode = resultCode;
            previous = attempt;
            if (!shouldRetrySearch) {
                return WebSearchPollExecutionOutcome.succeeded(
                        response, resultCode, attemptIndex + 1, elapsed(chainStarted));
            }
        }
        return WebSearchPollExecutionOutcome.succeeded(
                lastResponse, lastResultCode, MAX_SEARCH_ATTEMPTS, elapsed(chainStarted));
    }

    private PollInvocationAttempt createAttempt(WebSearchPollCommand command,
                                                PollInvocationAttempt previous,
                                                TriggerType triggerType,
                                                String systemPrompt) {
        AiPlatformConfig platform = command.platform();
        String providerConfig = normalizedProviderConfig(platform.getProviderConfigJson());
        PollInvocationAttempt draft = new PollInvocationAttempt();
        draft.setPollResultId(command.pollResultId());
        draft.setShardItemId(command.shardItemId());
        draft.setDispatchTaskId(command.dispatchTaskId());
        draft.setChainNo(1);
        draft.setRootAttemptId(previous == null ? null : previous.getRootAttemptId());
        draft.setRetryOfAttemptId(previous == null ? null : previous.getId());
        draft.setTriggerType(triggerType.name());
        draft.setProjectId(command.projectId());
        draft.setKeywordResultId(command.keywordResultId());
        draft.setQuestionSnapshot(command.question());
        draft.setSystemPromptSnapshot(systemPrompt);
        draft.setPlatformConfigId(platform.getId());
        draft.setPlatformCode(platform.getPlatformCode());
        draft.setChannelCode(StringUtils.hasText(platform.getChannelCode())
                ? platform.getChannelCode() : platform.getPlatformCode());
        draft.setProvider(provider(platform, providerConfig));
        draft.setIntegrationType(platform.getIntegrationType());
        draft.setRequestedModelId(platform.getModelId());
        draft.setEndpointUrl(platform.getApiUrl());
        draft.setConfigVersion(platform.getConfigVersion() == null ? 1L : platform.getConfigVersion());
        draft.setProviderConfigSnapshotJson(providerConfig);
        draft.setProviderConfigHash(sha256(providerConfig));
        draft.setBrandDictionaryVersion(sha256(writeJson(command.brandNames())));
        draft.setBrandDictionarySnapshotJson(writeJson(command.brandNames()));
        draft.setAdapterVersion(ADAPTER_VERSION);
        return attemptCreationService.create(
                draft,
                LocalDateTime.now(),
                Duration.ofMillis(command.requestTimeoutMs()),
                1,
                Duration.ZERO,
                DEADLINE_SAFETY_MARGIN
        );
    }

    private WebSearchPlatformProfile profile(AiPlatformConfig platform,
                                             int connectTimeoutMs,
                                             int requestTimeoutMs) {
        String providerConfig = normalizedProviderConfig(platform.getProviderConfigJson());
        return new WebSearchPlatformProfile(
                platform.getId(),
                platform.getPlatformCode(),
                StringUtils.hasText(platform.getChannelCode()) ? platform.getChannelCode() : platform.getPlatformCode(),
                provider(platform, providerConfig),
                IntegrationType.valueOf(platform.getIntegrationType()),
                platform.getApiUrl(),
                platform.getModelId(),
                credentialRef(platform),
                null,
                platform.getConfigVersion() == null ? 1L : platform.getConfigVersion(),
                providerConfig,
                sha256(providerConfig),
                connectTimeoutMs,
                requestTimeoutMs
        );
    }

    private String credentialRef(AiPlatformConfig platform) {
        if (StringUtils.hasText(platform.getPrimaryKeyRef())) {
            return platform.getPrimaryKeyRef().trim();
        }
        if (StringUtils.hasText(platform.getApiKey())) {
            return PlatformCredentialService.databaseCredentialRef(platform.getId());
        }
        throw new IllegalArgumentException(
                "Web-search platform requires a primary key reference or encrypted database credential");
    }

    private void finishFailure(PollInvocationAttempt attempt,
                               ErrorCategory category,
                               String errorCode,
                               String message) {
        LocalDateTime completedAt = LocalDateTime.now();
        resultWriter.writeFailure(attempt, category, errorCode, message, completedAt);
        lifecycleService.fail(attempt.getId(), completedAt);
        projectionService.finalizeAttempt(attempt.getId(), true, completedAt);
    }

    private String provider(AiPlatformConfig platform, String providerConfig) {
        try {
            JsonNode root = objectMapper.readTree(providerConfig);
            String provider = root.path("provider").asText(null);
            return StringUtils.hasText(provider) ? provider : platform.getPlatformCode();
        } catch (Exception ex) {
            return platform.getPlatformCode();
        }
    }

    private String normalizedProviderConfig(String providerConfig) {
        return StringUtils.hasText(providerConfig) ? providerConfig : "{}";
    }

    private String providerErrorCode(WebSearchProviderException ex) {
        return ex.httpStatus() == null ? ex.category().name() : "HTTP_" + ex.httpStatus();
    }

    private void validate(WebSearchPollCommand command) {
        if (command == null || command.platform() == null) {
            throw new IllegalArgumentException("Web-search poll command and platform are required");
        }
        IntegrationType integrationType = IntegrationType.valueOf(command.platform().getIntegrationType());
        if (!integrationType.isWebSearch()) {
            throw new IllegalArgumentException("Platform is not a web-search integration");
        }
        if (command.connectTimeoutMs() < 1 || command.requestTimeoutMs() < 1) {
            throw new IllegalArgumentException("Web-search timeouts must be positive");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize web-search snapshot", ex);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash web-search snapshot", ex);
        }
    }

    private long elapsed(long startedAt) {
        return Math.max(1L, System.currentTimeMillis() - startedAt);
    }
}
