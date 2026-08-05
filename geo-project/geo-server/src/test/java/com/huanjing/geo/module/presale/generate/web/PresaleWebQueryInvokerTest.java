package com.huanjing.geo.module.presale.generate.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.BrandMatchStrength;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchSource;
import com.huanjing.geo.module.presale.generate.web.provider.PresaleWebProvider;
import com.huanjing.geo.module.presale.generate.web.provider.PresaleWebProviderAttempt;
import com.huanjing.geo.module.presale.generate.web.provider.PresaleWebProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleWebQueryInvokerTest {
    @Mock
    private PresaleWebProvider provider;

    private PresaleWebQueryInvoker invoker;
    private ResolvedCompanionExecutionConfig config;
    private PresaleWebQueryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PresaleWebQueryProperties();
        properties.setMaxAttempts(2);
        properties.setMaxEvidenceBytes(64 * 1024);
        when(provider.integrationType()).thenReturn(IntegrationType.DASHSCOPE_NATIVE_WEB);
        invoker = new PresaleWebQueryInvoker(new ObjectMapper(), properties, List.of(provider));
        config = new ResolvedCompanionExecutionConfig(
                "qwen", "千问", 9L, "qwen_web", "千问联网", 3L, "qwen", "aliyun",
                IntegrationType.DASHSCOPE_NATIVE_WEB,
                "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
                "qwen-plus", "千问联网模型", "env://DASHSCOPE_API_KEY", "{}",
                10_000, 120_000, 2, 60_000, 60_000);
    }

    @Test
    void answerWithoutSearchEvidenceContinuesWithoutRetry() throws Exception {
        properties.setMaxAttempts(99);
        WebSearchResponse noEvidence = response("req-1", "answer", Map.of("prompt_tokens", 2), List.of());
        when(provider.execute(any(), anyString()))
                .thenReturn(new PresaleWebProviderAttempt(noEvidence, 10L));

        PresaleWebQueryResult result = invoker.invoke(config, "question");

        assertEquals("answer", result.callResult().rawResponse());
        assertEquals(1, result.evidence().physicalCallCount());
        assertEquals(2, result.evidence().promptTokens());
        assertTrue(!result.evidence().searchTriggered());
        verify(provider, times(1)).execute(any(), anyString());
    }

    @Test
    void emptyAnswerStillRetriesAndFailsAsInterfaceResponseFailure() throws Exception {
        WebSearchResponse emptyAnswer = response("req-1", "", Map.of(), List.of());
        when(provider.execute(any(), anyString()))
                .thenReturn(new PresaleWebProviderAttempt(emptyAnswer, 10L));

        PresaleWebQueryException failure = assertThrows(PresaleWebQueryException.class,
                () -> invoker.invoke(config, "question"));

        assertEquals("EMPTY_ANSWER", failure.getFailureCode());
        assertEquals(2, failure.getPartialEvidence().physicalCallCount());
        verify(provider, times(2)).execute(any(), anyString());
    }

    @Test
    void cancellationAfterFirstFailurePreventsSecondPhysicalCall() throws Exception {
        AtomicBoolean firstCallCompleted = new AtomicBoolean();
        when(provider.execute(any(), anyString())).thenAnswer(invocation -> {
            firstCallCompleted.set(true);
            throw new PresaleWebProviderException(
                    "HTTP_500", "retryable", true, "req-1", true, null);
        });

        assertThrows(InterruptedException.class, () -> invoker.invoke(config, "question", () -> {
            if (firstCallCompleted.get()) {
                throw new InterruptedException("canceled");
            }
        }));

        verify(provider, times(1)).execute(any(), anyString());
    }

    @Test
    void companionRpmGateSpacesPhysicalCalls() throws Exception {
        ResolvedCompanionExecutionConfig rateLimited = new ResolvedCompanionExecutionConfig(
                config.reportPlatformCode(), config.reportPlatformName(), config.companionConfigId(),
                config.companionPlatformCode(), config.companionPlatformName(), config.companionConfigVersion(),
                config.channelCode(), config.provider(), config.integrationType(), config.endpointUrl(),
                config.modelId(), config.modelName(), config.credentialRef(), config.providerConfigJson(),
                config.connectTimeoutMs(), config.requestTimeoutMs(), config.concurrencyLimit(), 1_200,
                config.tpmLimit());
        WebSearchSource source = new WebSearchSource(0, 1, "query", "title",
                "https://example.com", "https://example.com", "example.com", "web", "snippet",
                null, BrandMatchStrength.NONE, List.of());
        when(provider.execute(any(), anyString())).thenReturn(
                new PresaleWebProviderAttempt(response("req", "answer", Map.of(), List.of(source)), 1L));

        invoker.invoke(rateLimited, "first");
        long started = System.nanoTime();
        invoker.invoke(rateLimited, "second");
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        assertTrue(elapsedMs >= 30L, "RPM gate should delay the second physical call");
    }

    @Test
    void interruptedExecutionDoesNotCallOrRetryProvider() throws Exception {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> invoker.invoke(config, "question"));
            verify(provider, never()).execute(any(), anyString());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void successfulRetryAccumulatesCallsUsageAndRequestIdsButUsesSuccessfulEvidence() throws Exception {
        WebSearchResponse first = response("req-failed", "", Map.of("prompt_tokens", 3), List.of());
        WebSearchSource source = new WebSearchSource(0, 1, "query", "title",
                "https://example.com/a", "https://example.com/a", "example.com", null,
                "snippet", null, BrandMatchStrength.NONE, List.of());
        WebSearchResponse second = response("req-success", "answer with source",
                Map.of("prompt_tokens", 5, "completion_tokens", 7), List.of(source));
        when(provider.execute(any(), anyString()))
                .thenReturn(new PresaleWebProviderAttempt(first, 10L))
                .thenReturn(new PresaleWebProviderAttempt(second, 20L));

        PresaleWebQueryResult result = invoker.invoke(config, "question");

        assertEquals(2, result.evidence().physicalCallCount());
        assertEquals(List.of("req-failed", "req-success"), result.evidence().providerRequestIds());
        assertEquals(8, result.callResult().promptTokens());
        assertEquals(7, result.callResult().completionTokens());
        assertEquals(1, result.evidence().sources().size());
        assertTrue(result.evidenceJson().contains("WEB_SEARCH_V1"));
    }

    private WebSearchResponse response(String requestId,
                                       String answer,
                                       Map<String, Object> usage,
                                       List<WebSearchSource> sources) {
        return new WebSearchResponse(requestId, "qwen-plus", "qwen-plus", answer,
                SearchStatus.TRIGGERED, false, List.of(), sources, List.of(), usage, "stop");
    }
}
