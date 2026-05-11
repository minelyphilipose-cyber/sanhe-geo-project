package com.huanjing.geo.common.llm.router;

import com.huanjing.geo.common.llm.LlmInvokeException;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.pool.LlmExecutionGateway;
import com.huanjing.geo.common.llm.pool.LlmExecutionPermit;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.module.dispatch.service.PlatformRateLimiterService;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmPlatformRouter {
    private final LlmPlatformSelectionStrategy selectionStrategy;
    private final PlatformRateLimiterService platformRateLimiterService;
    private final LlmCircuitBreakerService circuitBreakerService;
    private final LlmInvoker llmInvoker;
    private final LlmExecutionGateway executionGateway;

    public LlmRouteResult invoke(LlmRouteRequest request) {
        List<LlmPlatformCandidate> candidates = selectionStrategy.selectCandidates(request);
        if (candidates.isEmpty()) {
            throw new LlmRouteException(LlmRouteFailureKind.NO_CANDIDATE,
                    "No LLM platform candidate for feature " + request.feature(), 0, null);
        }

        int requestCount = 0;
        int rateLimited = 0;
        int circuitOpen = 0;
        int permitBusy = 0;
        Exception lastError = null;

        for (LlmPlatformCandidate candidate : candidates) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new LlmRouteException(LlmRouteFailureKind.INTERRUPTED, "Interrupted during LLM routing",
                        requestCount, lastError);
            }
            if (!circuitBreakerService.allowRequest(candidate.platformCode())) {
                circuitOpen++;
                continue;
            }
            requestCount++;
            try (LlmExecutionPermit ignored = executionGateway.acquire(request.feature(), candidate.platformConfig())) {
                AiPlatformConfig config = candidate.platformConfig();
                if (!platformRateLimiterService.tryAcquire(config, request.tokenCost())) {
                    rateLimited++;
                    requestCount--;
                    continue;
                }
                LlmInvokeResult result = llmInvoker.invoke(request.userPrompt(), buildModelConfig(request, candidate));
                circuitBreakerService.recordSuccess(candidate.platformCode());
                return new LlmRouteResult(
                        candidate.platformCode(),
                        candidate.platformName(),
                        candidate.channel(),
                        candidate.modelId(),
                        candidate.modelName(),
                        result.responseText(),
                        result.durationMs(),
                        requestCount,
                        result
                );
            } catch (LlmPermitUnavailableException ex) {
                permitBusy++;
                lastError = ex;
                log.debug("LLM permit busy, feature={}, platform={}, channel={}",
                        request.feature(), candidate.platformCode(), candidate.channel());
            } catch (LlmInvokeException ex) {
                lastError = ex;
                circuitBreakerService.recordFailure(candidate.platformCode());
                log.warn("LLM candidate failed, feature={}, platform={}, channel={}, reason={}",
                        request.feature(), candidate.platformCode(), candidate.channel(), ex.getMessage());
            }
        }

        LlmRouteFailureKind kind = resolveFailureKind(candidates.size(), rateLimited, circuitOpen, permitBusy);
        String message = switch (kind) {
            case ALL_RATE_LIMITED -> "All LLM candidates are rate limited";
            case ALL_PERMIT_BUSY -> "All LLM candidates are waiting for permits";
            case ALL_CIRCUIT_OPEN -> "All LLM candidates are circuit-open";
            default -> lastError == null ? "All LLM candidates failed" : lastError.getMessage();
        };
        throw new LlmRouteException(kind, message, requestCount, lastError);
    }

    private LlmModelConfig buildModelConfig(LlmRouteRequest request, LlmPlatformCandidate candidate) {
        AiPlatformConfig config = candidate.platformConfig();
        return new LlmModelConfig(
                candidate.platformCode(),
                candidate.platformName(),
                candidate.modelId(),
                candidate.modelName(),
                candidate.apiUrl(),
                candidate.apiKey(),
                request.systemPrompt(),
                request.temperature(),
                request.connectTimeoutMs(),
                request.requestTimeoutMs(),
                request.maxRetry() == null ? config.getMaxRetry() : request.maxRetry(),
                config.getRateLimitQps(),
                request.maxTokens(),
                request.normalizeJsonOutput(),
                request.requestTimeoutMaxMs(),
                request.feature(),
                config.getConcurrencyLimit(),
                false
        );
    }

    private LlmRouteFailureKind resolveFailureKind(int total, int rateLimited, int circuitOpen, int permitBusy) {
        if (rateLimited == total) {
            return LlmRouteFailureKind.ALL_RATE_LIMITED;
        }
        if (permitBusy == total) {
            return LlmRouteFailureKind.ALL_PERMIT_BUSY;
        }
        if (circuitOpen == total) {
            return LlmRouteFailureKind.ALL_CIRCUIT_OPEN;
        }
        if (rateLimited + circuitOpen + permitBusy == total) {
            if (permitBusy > 0) {
                return LlmRouteFailureKind.ALL_PERMIT_BUSY;
            }
            if (rateLimited > 0) {
                return LlmRouteFailureKind.ALL_RATE_LIMITED;
            }
            return LlmRouteFailureKind.ALL_CIRCUIT_OPEN;
        }
        return LlmRouteFailureKind.ALL_FAILED;
    }
}
