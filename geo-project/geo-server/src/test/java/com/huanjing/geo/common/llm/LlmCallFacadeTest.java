package com.huanjing.geo.common.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.limiter.PlatformConcurrencyLimiterService;
import com.huanjing.geo.common.llm.limiter.PlatformRateLimiterService;
import com.huanjing.geo.common.llm.measurement.LlmMeasurementCollector;
import com.huanjing.geo.common.llm.measurement.RetryAfterParser;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmPlatformRouter;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmCallFacadeTest {

    @Test
    void directDelegatesToInvokerAndReturnsInvokeResult() throws Exception {
        LlmPlatformRouter router = mock(LlmPlatformRouter.class);
        LlmInvoker invoker = mock(LlmInvoker.class);
        PlatformRateLimiterService rateLimiter = mock(PlatformRateLimiterService.class);
        LlmHttpClient httpClient = mock(LlmHttpClient.class);
        LlmCallFacade facade = facade(router, invoker, rateLimiter, new PlatformConcurrencyLimiterService(), httpClient);
        LlmModelConfig modelConfig = modelConfig("direct");
        LlmInvokeResult invokeResult = invokeResult("direct response");
        when(invoker.invoke("prompt", modelConfig)).thenReturn(invokeResult);

        LlmCallResult result = facade.execute(LlmCallRequest.direct("prompt", modelConfig));

        assertEquals(invokeResult, result.invokeResult());
        verify(invoker).invoke("prompt", modelConfig);
        verify(router, never()).invoke(any());
    }

    @Test
    void routedDelegatesToRouterAndReturnsRouteResult() throws Exception {
        LlmPlatformRouter router = mock(LlmPlatformRouter.class);
        LlmInvoker invoker = mock(LlmInvoker.class);
        PlatformRateLimiterService rateLimiter = mock(PlatformRateLimiterService.class);
        LlmHttpClient httpClient = mock(LlmHttpClient.class);
        LlmCallFacade facade = facade(router, invoker, rateLimiter, new PlatformConcurrencyLimiterService(), httpClient);
        LlmRouteRequest request = new LlmRouteRequest(
                LlmFeature.ARTICLE, "system", "user", 0.1D,
                1000, 2000, LlmModelConfig.MAX_REQUEST_TIMEOUT_MS,
                0, null, false, 1, 0, List.of(platform("p")));
        LlmRouteResult routeResult = new LlmRouteResult(
                "p", "Platform", "primary", "model", "Model",
                "routed response", 12L, 2, invokeResult("routed response"));
        when(router.invoke(request)).thenReturn(routeResult);

        LlmCallResult result = facade.execute(LlmCallRequest.routed(request));

        assertEquals(routeResult, result.routeResult());
        verify(router).invoke(request);
        verify(invoker, never()).invoke(any(), any());
    }

    @Test
    void legacyLimiterAppliesRateLimiterAndReleasesConcurrencyPermit() {
        LlmPlatformRouter router = mock(LlmPlatformRouter.class);
        LlmInvoker invoker = mock(LlmInvoker.class);
        PlatformRateLimiterService rateLimiter = mock(PlatformRateLimiterService.class);
        LlmHttpClient httpClient = mock(LlmHttpClient.class);
        PlatformConcurrencyLimiterService concurrencyLimiter = new PlatformConcurrencyLimiterService();
        LlmCallFacade facade = facade(router, invoker, rateLimiter, concurrencyLimiter, httpClient);
        AiPlatformConfig platform = platform("legacy");
        platform.setConcurrencyLimit(1);
        when(rateLimiter.tryAcquire(platform, 1000)).thenReturn(true);
        try {
            when(httpClient.postJson(any(), any(), any(), eq(1000), eq(2000)))
                    .thenReturn(new LlmHttpClient.HttpResponse(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            LlmCallResult first = facade.execute(legacyRequest(platform));
            LlmCallResult second = facade.execute(legacyRequest(platform));
            assertThat(first.rawResponseText()).contains("\"choices\"");
            assertThat(second.rawResponseText()).contains("\"choices\"");
        });
        verify(rateLimiter, org.mockito.Mockito.times(2)).tryAcquire(platform, 1000);
    }

    @Test
    void legacyLimiterKeepsRateLimitFailureAsBizException429AndSkipsHttp() {
        LlmPlatformRouter router = mock(LlmPlatformRouter.class);
        LlmInvoker invoker = mock(LlmInvoker.class);
        PlatformRateLimiterService rateLimiter = mock(PlatformRateLimiterService.class);
        LlmHttpClient httpClient = mock(LlmHttpClient.class);
        LlmCallFacade facade = facade(router, invoker, rateLimiter, new PlatformConcurrencyLimiterService(), httpClient);
        AiPlatformConfig platform = platform("legacy");
        when(rateLimiter.tryAcquire(platform, 1000)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> facade.execute(legacyRequest(platform)));

        assertEquals(429, ex.getCode());
        assertEquals("Platform limited: legacy", ex.getMessage());
        try {
            verify(httpClient, never()).postJson(any(), any(), any(), any(Integer.class), any(Integer.class));
        } catch (Exception verifyException) {
            throw new IllegalStateException(verifyException);
        }
    }

    @Test
    void legacyHttpKeepsNon2xxAsBizExceptionWithStatusCode() throws Exception {
        LlmPlatformRouter router = mock(LlmPlatformRouter.class);
        LlmInvoker invoker = mock(LlmInvoker.class);
        PlatformRateLimiterService rateLimiter = mock(PlatformRateLimiterService.class);
        LlmHttpClient httpClient = mock(LlmHttpClient.class);
        LlmCallFacade facade = facade(router, invoker, rateLimiter, new PlatformConcurrencyLimiterService(), httpClient);
        AiPlatformConfig platform = platform("legacy");
        when(rateLimiter.tryAcquire(platform, 1000)).thenReturn(true);
        when(httpClient.postJson(any(), any(), any(), eq(1000), eq(2000)))
                .thenReturn(new LlmHttpClient.HttpResponse(429, "{\"error\":{\"code\":\"rate_limit_exceeded\"}}",
                        Map.of("Retry-After", List.of("2"))));

        BizException ex = assertThrows(BizException.class, () -> facade.execute(legacyRequest(platform)));

        assertEquals(429, ex.getCode());
        assertThat(ex.getMessage()).contains("Model API HTTP 429");
        assertThat(ex.getCause()).isInstanceOf(com.huanjing.geo.common.llm.measurement.LlmHttpErrorException.class);
        var cause = (com.huanjing.geo.common.llm.measurement.LlmHttpErrorException) ex.getCause();
        assertEquals("rate_limit_exceeded", cause.providerErrorCode());
        assertEquals(2000L, cause.retryAfterMs());
    }

    @Test
    void retryAfterParserSupportsSecondsAndHttpDate() {
        assertEquals(3000L, RetryAfterParser.parse("3"));
        String retryAt = ZonedDateTime.now().plusSeconds(2).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        assertThat(RetryAfterParser.parse(retryAt)).isBetween(0L, 3000L);
        assertThat(RetryAfterParser.parse("not-a-retry-after")).isNull();
    }

    private LlmCallFacade facade(LlmPlatformRouter router,
                                 LlmInvoker invoker,
                                 PlatformRateLimiterService rateLimiter,
                                 PlatformConcurrencyLimiterService concurrencyLimiter,
                                 LlmHttpClient httpClient) {
        return new LlmCallFacade(
                router,
                invoker,
                rateLimiter,
                concurrencyLimiter,
                httpClient,
                new ObjectMapper(),
                mock(LlmMeasurementCollector.class)
        );
    }

    private LlmCallRequest legacyRequest(AiPlatformConfig platform) {
        return LlmCallRequest.legacy(
                platform,
                platform.getPlatformCode(),
                platform.getPlatformName(),
                "primary",
                platform.getApiUrl(),
                platform.getModelId(),
                "sk-test",
                "system",
                "user",
                0D,
                1000,
                1000,
                2000,
                LlmFeature.GENERIC,
                1
        );
    }

    private AiPlatformConfig platform(String platformCode) {
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setPlatformCode(platformCode);
        platform.setPlatformName("Platform");
        platform.setApiUrl("https://example.test/v1");
        platform.setModelId("model");
        platform.setModelName("Model");
        platform.setConcurrencyLimit(1);
        return platform;
    }

    private LlmModelConfig modelConfig(String platformCode) {
        return new LlmModelConfig(
                platformCode,
                "Platform",
                "model",
                "Model",
                "https://example.test/v1",
                "sk-test",
                "system",
                0D,
                1000,
                2000,
                0,
                1,
                null,
                false
        );
    }

    private LlmInvokeResult invokeResult(String responseText) {
        return new LlmInvokeResult(
                responseText,
                1,
                2,
                3L,
                0,
                LlmCallStatus.SUCCESS,
                "p",
                "Platform",
                "model",
                "Model"
        );
    }
}
