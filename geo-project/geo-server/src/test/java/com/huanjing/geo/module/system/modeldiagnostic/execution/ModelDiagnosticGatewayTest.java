package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.llm.LlmHttpClient;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchCodecRequest;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchMessage;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.transport.PollPayloadProtector;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticGatewayTest {

    private final LlmHttpClient httpClient = mock(LlmHttpClient.class);
    private final PlatformCredentialService credentialService = mock(PlatformCredentialService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PollPayloadProtector payloadProtector = new PollPayloadProtector(objectMapper, "");

    @BeforeEach
    void setUp() {
        when(credentialService.resolvePrimaryCredentialStrict(any(), any())).thenReturn("secret");
    }

    @Test
    void basicChatMakesExactlyOneCallAndPreservesMultiTurnMessages() throws Exception {
        when(httpClient.postJson(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(new LlmHttpClient.HttpResponse(200, """
                        {"id":"req-1","model":"response-model","choices":[{
                          "message":{"content":"answer"},"finish_reason":"stop"
                        }],"usage":{"prompt_tokens":7,"completion_tokens":3,"total_tokens":10}}
                        """));
        ModelDiagnosticGateway gateway = gateway(List.of());

        ModelDiagnosticProviderResult result = gateway.execute(new ModelDiagnosticProviderRequest(
                profile(IntegrationType.OPENAI_CHAT), "system",
                List.of(new WebSearchMessage("user", "first"),
                        new WebSearchMessage("assistant", "old answer"),
                        new WebSearchMessage("user", "next")),
                LocalDateTime.now().plusMinutes(1)));

        assertEquals("answer", result.answer());
        assertEquals("req-1", result.providerRequestId());
        assertEquals(7, result.usage().get("prompt_tokens"));
        assertNotNull(result.sanitizedRequest());
        verify(httpClient, times(1)).postJson(anyString(), any(), anyString(), anyInt(), anyInt());
    }

    @Test
    void missingStrictPrimaryCredentialFailsBeforeHttpCall() throws Exception {
        when(credentialService.resolvePrimaryCredentialStrict(any(), any())).thenReturn(null);
        ModelDiagnosticGateway gateway = gateway(List.of());

        ModelDiagnosticExecutionException error = assertThrows(
                ModelDiagnosticExecutionException.class,
                () -> gateway.execute(request(IntegrationType.OPENAI_CHAT)));

        assertEquals(ErrorCategory.AUTHENTICATION, error.category());
        assertNotNull(error.sanitizedRequest());
        verify(httpClient, never()).postJson(anyString(), any(), anyString(), anyInt(), anyInt());
    }

    @Test
    void provider429IsNotRetried() throws Exception {
        when(httpClient.postJson(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(new LlmHttpClient.HttpResponse(429, "{\"error\":\"rate limit\"}"));
        ModelDiagnosticGateway gateway = gateway(List.of());

        ModelDiagnosticExecutionException error = assertThrows(
                ModelDiagnosticExecutionException.class,
                () -> gateway.execute(request(IntegrationType.OPENAI_CHAT)));

        assertEquals(ErrorCategory.RATE_LIMIT, error.category());
        verify(httpClient, times(1)).postJson(anyString(), any(), anyString(), anyInt(), anyInt());
    }

    @Test
    void modelNotOpenResponseProducesAnActionableSanitizedFailure() throws Exception {
        when(httpClient.postJson(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(new LlmHttpClient.HttpResponse(404, """
                        {"error":{"code":"ModelNotOpen",
                        "message":"Your account 123 has not activated the model"}}
                        """));
        ModelDiagnosticGateway gateway = gateway(List.of());

        ModelDiagnosticExecutionException error = assertThrows(
                ModelDiagnosticExecutionException.class,
                () -> gateway.execute(request(IntegrationType.OPENAI_CHAT)));

        assertEquals(ErrorCategory.INVALID_REQUEST, error.category());
        assertEquals(404, error.httpStatus());
        assertEquals("Provider returned HTTP 404 (ModelNotOpen): "
                + "activate the requested model in the provider console", error.getMessage());
        assertNotNull(error.sanitizedResponse());
    }

    @Test
    void webSearchUsesPureCodecDirectly() throws Exception {
        WebSearchCodec codec = mock(WebSearchCodec.class);
        when(codec.integrationType()).thenReturn(IntegrationType.VOLCENGINE_RESPONSES_WEB);
        when(codec.encode(any())).thenReturn(objectMapper.createObjectNode().put("encoded", true));
        when(codec.decode(any(JsonNode.class), any(WebSearchCodecRequest.class)))
                .thenReturn(new WebSearchResponse(
                        "web-req", "model", "model", "web answer", SearchStatus.TRIGGERED,
                        false, List.of(), List.of(), List.of(), Map.of("input_tokens", 2), "stop"));
        when(httpClient.postJson(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(new LlmHttpClient.HttpResponse(200, "{}"));
        ModelDiagnosticGateway gateway = gateway(List.of(codec));

        ModelDiagnosticProviderResult result = gateway.execute(
                request(IntegrationType.VOLCENGINE_RESPONSES_WEB));

        assertEquals(SearchStatus.TRIGGERED, result.searchStatus());
        assertEquals("web answer", result.answer());
        verify(codec).encode(any(WebSearchCodecRequest.class));
        verify(codec).decode(any(JsonNode.class), any(WebSearchCodecRequest.class));
    }

    private ModelDiagnosticGateway gateway(List<WebSearchCodec> codecs) {
        return new ModelDiagnosticGateway(
                httpClient, objectMapper, payloadProtector, credentialService, codecs);
    }

    private ModelDiagnosticProviderRequest request(IntegrationType integrationType) {
        return new ModelDiagnosticProviderRequest(
                profile(integrationType), "system",
                List.of(new WebSearchMessage("user", "question")),
                LocalDateTime.now().plusMinutes(1));
    }

    private ModelDiagnosticPlatformProfile profile(IntegrationType integrationType) {
        return new ModelDiagnosticPlatformProfile(
                1L, "platform", "channel", "Platform", "QUESTION_POLL_WEB",
                integrationType, "https://example.test/v1", "model", 1L,
                "{}", "{}", "hash", "env://TEST_KEY", null,
                3_000, 60_000);
    }
}
