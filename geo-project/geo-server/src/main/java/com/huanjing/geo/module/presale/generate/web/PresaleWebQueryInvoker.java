package com.huanjing.geo.module.presale.generate.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.model.SearchEvidence;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchCitation;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchSource;
import com.huanjing.geo.module.presale.generate.llm.CallStatus;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.web.provider.PresaleWebProvider;
import com.huanjing.geo.module.presale.generate.web.provider.PresaleWebProviderAttempt;
import com.huanjing.geo.module.presale.generate.web.provider.PresaleWebProviderException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

@Service
public class PresaleWebQueryInvoker {
    private final ObjectMapper objectMapper;
    private final PresaleWebQueryProperties properties;
    private final Map<com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType, PresaleWebProvider> providers;
    private final Map<Long, Semaphore> companionSemaphores = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> companionNextRequestNanos = new ConcurrentHashMap<>();

    public PresaleWebQueryInvoker(ObjectMapper objectMapper,
                                  PresaleWebQueryProperties properties,
                                  List<PresaleWebProvider> providerList) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        EnumMap<com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType, PresaleWebProvider> map =
                new EnumMap<>(com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType.class);
        for (PresaleWebProvider provider : providerList) {
            if (map.put(provider.integrationType(), provider) != null) {
                throw new IllegalStateException("Duplicate presale web provider for " + provider.integrationType());
            }
        }
        this.providers = Map.copyOf(map);
    }

    public PresaleWebQueryResult invoke(ResolvedCompanionExecutionConfig config,
                                        String userPrompt) throws PresaleWebQueryException, InterruptedException {
        return invoke(config, userPrompt, this::checkThreadActive);
    }

    public PresaleWebQueryResult invoke(ResolvedCompanionExecutionConfig config,
                                        String userPrompt,
                                        PresaleWebExecutionGuard executionGuard)
            throws PresaleWebQueryException, InterruptedException {
        PresaleWebProvider provider = providers.get(config.integrationType());
        if (provider == null) {
            PresaleSearchEvidence evidence = evidence(config, false, "FAILED", PresaleEvidenceLevel.NONE,
                    "WEB_PROVIDER_MISSING", List.of(), 0, null, null, List.of(), List.of(), List.of());
            throw failure("WEB_PROVIDER_MISSING", "No presale web provider for " + config.integrationType(), evidence, null);
        }

        int maxAttempts = Math.min(2, Math.max(1, properties.getMaxAttempts()));
        List<String> requestIds = new ArrayList<>();
        int physicalCalls = 0;
        int promptTokens = 0;
        int completionTokens = 0;
        boolean hasPromptTokens = false;
        boolean hasCompletionTokens = false;
        long totalDurationMs = 0L;
        String lastFailureCode = "WEB_QUERY_FAILED";
        Throwable lastCause = null;
        WebSearchResponse lastResponse = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            checkActive(executionGuard);
            try {
                PresaleWebProviderAttempt result = withCompanionPermit(
                        config, provider, userPrompt, executionGuard);
                physicalCalls++;
                totalDurationMs += result.durationMs();
                lastResponse = result.response();
                addRequestId(requestIds, lastResponse.providerRequestId());
                Integer attemptPromptTokens = usageInt(lastResponse.usage(), "prompt_tokens", "input_tokens");
                Integer attemptCompletionTokens = usageInt(lastResponse.usage(), "completion_tokens", "output_tokens");
                if (attemptPromptTokens != null) {
                    promptTokens += attemptPromptTokens;
                    hasPromptTokens = true;
                }
                if (attemptCompletionTokens != null) {
                    completionTokens += attemptCompletionTokens;
                    hasCompletionTokens = true;
                }

                PresaleEvidenceLevel level = evidenceLevel(lastResponse);
                if (StringUtils.hasText(lastResponse.answer())) {
                    boolean usableSearchEvidence = lastResponse.searchStatus().hasUsableSources()
                            && level != PresaleEvidenceLevel.NONE;
                    PresaleSearchEvidence evidence = evidence(config, usableSearchEvidence,
                            "SUCCEEDED", level, null,
                            requestIds, physicalCalls,
                            hasPromptTokens ? promptTokens : null,
                            hasCompletionTokens ? completionTokens : null,
                            sanitizeSearchEvidence(lastResponse.searchEvidence()),
                            sanitizeSources(lastResponse.sources()),
                            sanitizeCitations(lastResponse.citations()));
                    String evidenceJson = serializeBounded(evidence);
                    LlmCallResult callResult = new LlmCallResult(lastResponse.answer(),
                            hasPromptTokens ? promptTokens : null,
                            hasCompletionTokens ? completionTokens : null,
                            totalDurationMs, attempt - 1, CallStatus.SUCCESS,
                            config.companionPlatformCode(), config.companionPlatformName(),
                            config.modelId(), config.modelName());
                    return new PresaleWebQueryResult(callResult, evidence, evidenceJson);
                }
                lastFailureCode = "EMPTY_ANSWER";
                lastCause = null;
            } catch (PresaleWebProviderException ex) {
                if (ex.physicalCallOccurred()) physicalCalls++;
                addRequestId(requestIds, ex.providerRequestId());
                lastFailureCode = ex.failureCode();
                lastCause = ex;
                if (!ex.retryable()) {
                    break;
                }
            }
            if (attempt < maxAttempts) {
                // Cancellation or status changes between attempts must prevent another physical call.
                checkActive(executionGuard);
            }
        }

        PresaleEvidenceLevel partialLevel = lastResponse == null
                ? PresaleEvidenceLevel.NONE : evidenceLevel(lastResponse);
        PresaleSearchEvidence partial = evidence(config,
                lastResponse != null && lastResponse.searchStatus().hasUsableSources(),
                "FAILED", partialLevel,
                lastFailureCode, requestIds, physicalCalls,
                hasPromptTokens ? promptTokens : null,
                hasCompletionTokens ? completionTokens : null,
                lastResponse == null ? List.of() : sanitizeSearchEvidence(lastResponse.searchEvidence()),
                lastResponse == null ? List.of() : sanitizeSources(lastResponse.sources()),
                lastResponse == null ? List.of() : sanitizeCitations(lastResponse.citations()));
        throw failure(lastFailureCode, "Presale web QUERY failed: " + lastFailureCode, partial, lastCause);
    }

    private PresaleWebProviderAttempt withCompanionPermit(ResolvedCompanionExecutionConfig config,
                                                          PresaleWebProvider provider,
                                                          String userPrompt,
                                                          PresaleWebExecutionGuard executionGuard)
            throws PresaleWebProviderException, InterruptedException {
        Semaphore semaphore = companionSemaphores.computeIfAbsent(config.companionConfigId(),
                ignored -> new Semaphore(Math.max(1, config.concurrencyLimit())));
        semaphore.acquire();
        try {
            checkActive(executionGuard);
            acquireRpmPermit(config, executionGuard);
            checkActive(executionGuard);
            return provider.execute(config, userPrompt);
        } finally {
            semaphore.release();
        }
    }

    private void acquireRpmPermit(ResolvedCompanionExecutionConfig config,
                                  PresaleWebExecutionGuard executionGuard) throws InterruptedException {
        int rpm = Math.max(1, config.rpmLimit());
        long intervalNanos = Math.max(1L, 60_000_000_000L / rpm);
        AtomicLong nextRequest = companionNextRequestNanos.computeIfAbsent(
                config.companionConfigId(), ignored -> new AtomicLong());
        while (true) {
            checkActive(executionGuard);
            long now = System.nanoTime();
            long current = nextRequest.get();
            if (current <= now) {
                long next = now >= Long.MAX_VALUE - intervalNanos
                        ? Long.MAX_VALUE : now + intervalNanos;
                if (nextRequest.compareAndSet(current, next)) {
                    return;
                }
                continue;
            }
            long remaining = current - now;
            if (remaining <= 0L) {
                return;
            }
            LockSupport.parkNanos(Math.min(remaining, 100_000_000L));
            checkThreadActive();
        }
    }

    private void checkActive(PresaleWebExecutionGuard executionGuard) throws InterruptedException {
        checkThreadActive();
        if (executionGuard != null) {
            executionGuard.checkActive();
        }
        checkThreadActive();
    }

    private void checkThreadActive() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("presale web QUERY interrupted");
        }
    }

    private PresaleSearchEvidence evidence(ResolvedCompanionExecutionConfig config,
                                           boolean searchTriggered,
                                           String searchStatus,
                                           PresaleEvidenceLevel level,
                                           String failureCode,
                                           List<String> requestIds,
                                           int physicalCalls,
                                           Integer promptTokens,
                                           Integer completionTokens,
                                           List<SearchEvidence> searchEvidence,
                                           List<WebSearchSource> sources,
                                           List<WebSearchCitation> citations) {
        Map<String, Object> usage = new LinkedHashMap<>();
        if (promptTokens != null) usage.put("prompt_tokens", promptTokens);
        if (completionTokens != null) usage.put("completion_tokens", completionTokens);
        if (promptTokens != null || completionTokens != null) {
            usage.put("total_tokens", (promptTokens == null ? 0 : promptTokens)
                    + (completionTokens == null ? 0 : completionTokens));
        }
        return new PresaleSearchEvidence(PresaleSearchEvidence.CONTRACT_VERSION,
                config.reportPlatformCode(), config.companionConfigId(), config.companionConfigVersion(),
                config.companionPlatformCode(), config.integrationType().name(), config.modelId(),
                searchTriggered, searchStatus, level, failureCode, requestIds, physicalCalls,
                promptTokens, completionTokens, usage, searchEvidence, sources, citations);
    }

    private PresaleEvidenceLevel evidenceLevel(WebSearchResponse response) {
        if (response == null) return PresaleEvidenceLevel.NONE;
        if (!response.citations().isEmpty()) return PresaleEvidenceLevel.CITATIONS;
        if (!response.sources().isEmpty()) return PresaleEvidenceLevel.SOURCES;
        if (!response.searchEvidence().isEmpty()) return PresaleEvidenceLevel.TOOL_EVENT;
        return PresaleEvidenceLevel.NONE;
    }

    private List<SearchEvidence> sanitizeSearchEvidence(List<SearchEvidence> input) {
        if (input == null) return List.of();
        return input.stream().limit(maxItems())
                .map(value -> new SearchEvidence(value.eventIndex(), truncate(value.evidenceType()),
                        truncate(value.query()), Map.of()))
                .toList();
    }

    private List<WebSearchSource> sanitizeSources(List<WebSearchSource> input) {
        if (input == null) return List.of();
        return input.stream().filter(this::hasSafeSourceUrl).limit(maxItems())
                .map(value -> new WebSearchSource(value.searchEventIndex(), value.rank(), truncate(value.query()),
                        truncate(value.title()), truncate(value.originalUrl()), truncate(value.normalizedUrl()),
                        truncate(value.domain()), truncate(value.media()), truncate(value.snippet()),
                        value.publishTime(), value.brandMatchStrength(),
                        value.matchedKeywords().stream().limit(maxItems()).map(this::truncate).toList()))
                .toList();
    }

    private List<WebSearchCitation> sanitizeCitations(List<WebSearchCitation> input) {
        if (input == null) return List.of();
        return input.stream().limit(maxItems())
                .map(value -> new WebSearchCitation(value.citationIndex(), value.sourceOccurrenceIndex(),
                        value.answerStart(), value.answerEnd(), truncate(value.citationText()),
                        value.confidence(), truncate(value.validationStatus())))
                .toList();
    }

    private boolean hasSafeSourceUrl(WebSearchSource source) {
        String raw = StringUtils.hasText(source.normalizedUrl()) ? source.normalizedUrl() : source.originalUrl();
        if (!StringUtils.hasText(raw)) return false;
        try {
            String scheme = URI.create(raw).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (Exception ex) {
            return false;
        }
    }

    private String serializeBounded(PresaleSearchEvidence evidence) {
        try {
            String json = objectMapper.writeValueAsString(evidence);
            if (json.getBytes(StandardCharsets.UTF_8).length <= Math.max(1_024, properties.getMaxEvidenceBytes())) {
                return json;
            }
            PresaleSearchEvidence compact = new PresaleSearchEvidence(evidence.queryContractVersion(),
                    evidence.reportPlatformCode(), evidence.webConfigId(), evidence.webConfigVersion(),
                    evidence.companionPlatformCode(), evidence.integrationType(), evidence.modelId(),
                    evidence.searchTriggered(), evidence.searchStatus(), evidence.evidenceLevel(), evidence.failureCode(),
                    evidence.providerRequestIds(), evidence.physicalCallCount(), evidence.promptTokens(),
                    evidence.completionTokens(), evidence.usage(), List.of(), List.of(), List.of());
            return objectMapper.writeValueAsString(compact);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize presale search evidence", ex);
        }
    }

    private PresaleWebQueryException failure(String code, String message,
                                             PresaleSearchEvidence evidence, Throwable cause) {
        return new PresaleWebQueryException(code, message, evidence, serializeBounded(evidence), cause);
    }

    private Integer usageInt(Map<String, Object> usage, String... keys) {
        if (usage == null) return null;
        for (String key : keys) {
            Object value = usage.get(key);
            if (value instanceof Number number) return number.intValue();
            if (value != null) {
                try { return Integer.valueOf(String.valueOf(value)); } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }

    private void addRequestId(List<String> ids, String value) {
        if (StringUtils.hasText(value) && !ids.contains(value) && ids.size() < maxItems()) ids.add(truncate(value));
    }

    private int maxItems() { return Math.max(1, properties.getMaxEvidenceItems()); }

    private String truncate(String value) {
        if (value == null) return null;
        int limit = Math.max(32, properties.getMaxTextLength());
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
