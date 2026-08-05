package com.huanjing.geo.module.dispatch.websearch.transport;

import com.huanjing.geo.common.llm.LlmHttpClient;
import com.huanjing.geo.module.dispatch.entity.PollProviderCall;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchPlatformProfile;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchRequest;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSearchProviderCallExecutorTest {

    private final LlmHttpClient httpClient = mock(LlmHttpClient.class);
    private final PlatformCredentialService credentialService = mock(PlatformCredentialService.class);
    private final ProviderCallAuditWriter auditWriter = mock(ProviderCallAuditWriter.class);
    private WebSearchProviderCallExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new WebSearchProviderCallExecutor(httpClient, credentialService, auditWriter);
        when(credentialService.resolveCredential(anyString(), any())).thenReturn("secret");
        PollProviderCall call = new PollProviderCall();
        call.setId(99L);
        when(auditWriter.start(any(), anyString(), any())).thenReturn(call);
    }

    @Test
    void maps429ToRetryableRateLimitAndAuditsFailure() throws Exception {
        when(httpClient.postJson(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(new LlmHttpClient.HttpResponse(429, "{\"error\":\"rate limit\"}"));

        WebSearchProviderException error = assertThrows(
                WebSearchProviderException.class, () -> executor.postJson(request(60), "{}"));

        assertEquals(ErrorCategory.RATE_LIMIT, error.category());
        verify(auditWriter).fail(
                org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.eq(429), anyString(),
                org.mockito.ArgumentMatchers.eq(ErrorCategory.RATE_LIMIT), anyString(), anyString(), any(), any(Long.class));
    }

    @Test
    void mapsTransportTimeoutAndAuditsFailure() throws Exception {
        when(httpClient.postJson(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenThrow(new HttpTimeoutException("timed out"));

        WebSearchProviderException error = assertThrows(
                WebSearchProviderException.class, () -> executor.postJson(request(60), "{}"));

        assertEquals(ErrorCategory.TIMEOUT, error.category());
        verify(auditWriter).fail(
                org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(ErrorCategory.TIMEOUT),
                anyString(), anyString(), any(), any(Long.class));
    }

    @Test
    void maps401ToNonRetryableAuthenticationFailure() throws Exception {
        when(httpClient.postJson(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(new LlmHttpClient.HttpResponse(401, "{\"error\":\"unauthorized\"}"));

        WebSearchProviderException error = assertThrows(
                WebSearchProviderException.class, () -> executor.postJson(request(60), "{}"));

        assertEquals(ErrorCategory.AUTHENTICATION, error.category());
        assertEquals(false, error.retryable());
        verify(auditWriter).fail(
                org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.eq(401), anyString(),
                org.mockito.ArgumentMatchers.eq(ErrorCategory.AUTHENTICATION), anyString(), anyString(),
                any(), any(Long.class));
    }

    @Test
    void elapsedAttemptDeadlineDoesNotCreateFakePhysicalCall() throws Exception {
        WebSearchProviderException error = assertThrows(
                WebSearchProviderException.class, () -> executor.postJson(request(-1), "{}"));

        assertEquals(ErrorCategory.TIMEOUT, error.category());
        verify(auditWriter, never()).start(any(), anyString(), any());
        verify(httpClient, never()).postJson(anyString(), any(), anyString(), anyInt(), anyInt());
    }

    @Test
    void mimoUsesApiKeyHeaderInsteadOfBearerAuthorization() throws Exception {
        when(httpClient.postJson(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(new LlmHttpClient.HttpResponse(200, "{}"));

        executor.postJson(request(60, IntegrationType.MIMO_CHAT_WEB), "{}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).postJson(anyString(), headers.capture(), anyString(), anyInt(), anyInt());
        assertEquals("secret", headers.getValue().get("api-key"));
        assertTrue(!headers.getValue().containsKey("Authorization"));
    }

    private WebSearchRequest request(int deadlineOffsetSeconds) {
        return request(deadlineOffsetSeconds, IntegrationType.VOLCENGINE_RESPONSES_WEB);
    }

    private WebSearchRequest request(int deadlineOffsetSeconds, IntegrationType integrationType) {
        WebSearchPlatformProfile profile = new WebSearchPlatformProfile(
                1L, "test_web", "test", "provider", integrationType,
                "https://example.test/responses", "model", "env://TEST_KEY", null,
                1L, "{}", "hash", 3_000, 60_000);
        return new WebSearchRequest(
                1L, 2L, "问题", "提示", profile,
                LocalDateTime.now().plusSeconds(deadlineOffsetSeconds));
    }
}
