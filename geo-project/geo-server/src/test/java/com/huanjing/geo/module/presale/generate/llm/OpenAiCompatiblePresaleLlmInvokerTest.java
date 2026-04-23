package com.huanjing.geo.module.presale.generate.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleLlmConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiCompatiblePresaleLlmInvokerTest {

    @Mock
    private PresaleLlmConfigMapper configMapper;
    @Mock
    private PlatformCredentialService credentialService;
    @Mock
    private PresaleLlmHttpClient httpClient;

    private OpenAiCompatiblePresaleLlmInvoker invoker;

    @BeforeEach
    void setUp() {
        invoker = new OpenAiCompatiblePresaleLlmInvoker(
                configMapper,
                credentialService,
                httpClient,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(invoker, "connectTimeoutMs", 10_000);
    }

    @Test
    void query_retriesOnceThenSuccess() throws Exception {
        PresaleLlmPlatformConfigRow row = row();
        when(configMapper.selectRuntimeConfig("kimi")).thenReturn(row);
        when(credentialService.resolveApiKey(anyString(), anyString(), anyString())).thenReturn("key");
        when(httpClient.postJson(anyString(), anyMap(), anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("temporary timeout"))
                .thenReturn(new PresaleLlmHttpClient.HttpResponse(200,
                        "{\"choices\":[{\"message\":{\"content\":\"query ok\"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":34}}"));

        PlatformCallContext ctx = new PlatformCallContext(
                1L, 1, "kimi", 1001L, "", "Acme", 11L, false
        );
        LlmCallResult result = invoker.query(ctx, "hello");

        assertEquals(CallStatus.SUCCESS, result.callStatus());
        assertEquals(1, result.retryCount());
        assertTrue(result.isRetriedSuccess());
        assertEquals("query ok", result.rawResponse());
        assertEquals(12, result.promptTokens());
        assertEquals(34, result.completionTokens());
    }

    @Test
    void analyze_stripsMarkdownFenceAndValidatesJson() throws Exception {
        PresaleLlmPlatformConfigRow row = row();
        when(configMapper.selectRuntimeConfig("kimi")).thenReturn(row);
        when(credentialService.resolveApiKey(anyString(), anyString(), anyString())).thenReturn("key");
        when(httpClient.postJson(anyString(), anyMap(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PresaleLlmHttpClient.HttpResponse(200,
                        "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"is_mentioned\\\":true,\\\"ranking\\\":1,\\\"sentiment\\\":\\\"POSITIVE\\\",\\\"mentioned_competitors\\\":[],\\\"scene_advantages\\\":[],\\\"top_keywords\\\":[{\\\"keyword\\\":\\\"性价比高\\\",\\\"sentiment\\\":\\\"POSITIVE\\\"}],\\\"negative_evidence\\\":{\\\"has_negative\\\":false,\\\"snippet\\\":null}}\\n```\"}}],\"usage\":{\"prompt_tokens\":22,\"completion_tokens\":18}}"));

        PlatformCallContext ctx = new PlatformCallContext(
                1L, 1, "kimi", 1002L, "", "Acme", 11L, false
        );
        LlmCallResult result = invoker.analyze(ctx, "问句", "回答");

        assertEquals(CallStatus.SUCCESS, result.callStatus());
        assertEquals(0, result.retryCount());
        assertTrue(result.rawResponse().contains("\"is_mentioned\":true"));
        assertEquals(22, result.promptTokens());
        assertEquals(18, result.completionTokens());
    }

    @Test
    void analyze_invalidTopKeywordsShape_throwsAnalyzeParseException() throws Exception {
        PresaleLlmPlatformConfigRow row = row();
        when(configMapper.selectRuntimeConfig("kimi")).thenReturn(row);
        when(credentialService.resolveApiKey(anyString(), anyString(), anyString())).thenReturn("key");
        when(httpClient.postJson(anyString(), anyMap(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PresaleLlmHttpClient.HttpResponse(200,
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"is_mentioned\\\":true,\\\"ranking\\\":1,\\\"sentiment\\\":\\\"POSITIVE\\\",\\\"mentioned_competitors\\\":[],\\\"scene_advantages\\\":[],\\\"top_keywords\\\":{},\\\"negative_evidence\\\":{\\\"has_negative\\\":false,\\\"snippet\\\":null}}\"}}]}"));

        PlatformCallContext ctx = new PlatformCallContext(
                1L, 1, "kimi", 1002L, "", "Acme", 11L, false
        );
        assertThrows(AnalyzeParseException.class, () -> invoker.analyze(ctx, "问句", "回答"));
    }

    @Test
    void analyze_invalidTopKeywordsElement_throwsAnalyzeParseException() throws Exception {
        PresaleLlmPlatformConfigRow row = row();
        when(configMapper.selectRuntimeConfig("kimi")).thenReturn(row);
        when(credentialService.resolveApiKey(anyString(), anyString(), anyString())).thenReturn("key");
        when(httpClient.postJson(anyString(), anyMap(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PresaleLlmHttpClient.HttpResponse(200,
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"is_mentioned\\\":true,\\\"ranking\\\":1,\\\"sentiment\\\":\\\"POSITIVE\\\",\\\"mentioned_competitors\\\":[],\\\"scene_advantages\\\":[],\\\"top_keywords\\\":[{\\\"keyword\\\":\\\"词A\\\",\\\"sentiment\\\":\\\"BAD\\\"}],\\\"negative_evidence\\\":{\\\"has_negative\\\":false,\\\"snippet\\\":null}}\"}}]}"));

        PlatformCallContext ctx = new PlatformCallContext(
                1L, 1, "kimi", 1002L, "", "Acme", 11L, false
        );
        assertThrows(AnalyzeParseException.class, () -> invoker.analyze(ctx, "问句", "回答"));
    }

    @Test
    void analyze_topKeywordsElementMissingKeyword_throwsAnalyzeParseException() throws Exception {
        PresaleLlmPlatformConfigRow row = row();
        when(configMapper.selectRuntimeConfig("kimi")).thenReturn(row);
        when(credentialService.resolveApiKey(anyString(), anyString(), anyString())).thenReturn("key");
        when(httpClient.postJson(anyString(), anyMap(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PresaleLlmHttpClient.HttpResponse(200,
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"is_mentioned\\\":true,\\\"ranking\\\":1,\\\"sentiment\\\":\\\"POSITIVE\\\",\\\"mentioned_competitors\\\":[],\\\"scene_advantages\\\":[],\\\"top_keywords\\\":[{\\\"sentiment\\\":\\\"POSITIVE\\\"}],\\\"negative_evidence\\\":{\\\"has_negative\\\":false,\\\"snippet\\\":null}}\"}}]}"));

        PlatformCallContext ctx = new PlatformCallContext(
                1L, 1, "kimi", 1002L, "", "Acme", 11L, false
        );
        assertThrows(AnalyzeParseException.class, () -> invoker.analyze(ctx, "问句", "回答"));
    }

    @Test
    void analyze_invalidNegativeEvidenceShape_throwsAnalyzeParseException() throws Exception {
        PresaleLlmPlatformConfigRow row = row();
        when(configMapper.selectRuntimeConfig("kimi")).thenReturn(row);
        when(credentialService.resolveApiKey(anyString(), anyString(), anyString())).thenReturn("key");
        when(httpClient.postJson(anyString(), anyMap(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PresaleLlmHttpClient.HttpResponse(200,
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"is_mentioned\\\":true,\\\"ranking\\\":1,\\\"sentiment\\\":\\\"POSITIVE\\\",\\\"mentioned_competitors\\\":[],\\\"scene_advantages\\\":[],\\\"top_keywords\\\":[],\\\"negative_evidence\\\":[]}\"}}]}"));

        PlatformCallContext ctx = new PlatformCallContext(
                1L, 1, "kimi", 1002L, "", "Acme", 11L, false
        );
        assertThrows(AnalyzeParseException.class, () -> invoker.analyze(ctx, "问句", "回答"));
    }

    @Test
    void analyze_hasNegativeTrueButSnippetNull_throwsAnalyzeParseException() throws Exception {
        PresaleLlmPlatformConfigRow row = row();
        when(configMapper.selectRuntimeConfig("kimi")).thenReturn(row);
        when(credentialService.resolveApiKey(anyString(), anyString(), anyString())).thenReturn("key");
        when(httpClient.postJson(anyString(), anyMap(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PresaleLlmHttpClient.HttpResponse(200,
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"is_mentioned\\\":true,\\\"ranking\\\":1,\\\"sentiment\\\":\\\"POSITIVE\\\",\\\"mentioned_competitors\\\":[],\\\"scene_advantages\\\":[],\\\"top_keywords\\\":[],\\\"negative_evidence\\\":{\\\"has_negative\\\":true,\\\"snippet\\\":null}}\"}}]}"));

        PlatformCallContext ctx = new PlatformCallContext(
                1L, 1, "kimi", 1002L, "", "Acme", 11L, false
        );
        assertThrows(AnalyzeParseException.class, () -> invoker.analyze(ctx, "问句", "回答"));
    }

    @Test
    void analyze_negativeEvidenceMissingHasNegative_throwsAnalyzeParseException() throws Exception {
        PresaleLlmPlatformConfigRow row = row();
        when(configMapper.selectRuntimeConfig("kimi")).thenReturn(row);
        when(credentialService.resolveApiKey(anyString(), anyString(), anyString())).thenReturn("key");
        when(httpClient.postJson(anyString(), anyMap(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PresaleLlmHttpClient.HttpResponse(200,
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"is_mentioned\\\":true,\\\"ranking\\\":1,\\\"sentiment\\\":\\\"POSITIVE\\\",\\\"mentioned_competitors\\\":[],\\\"scene_advantages\\\":[],\\\"top_keywords\\\":[],\\\"negative_evidence\\\":{\\\"snippet\\\":null}}\"}}]}"));

        PlatformCallContext ctx = new PlatformCallContext(
                1L, 1, "kimi", 1002L, "", "Acme", 11L, false
        );
        assertThrows(AnalyzeParseException.class, () -> invoker.analyze(ctx, "问句", "回答"));
    }

    private PresaleLlmPlatformConfigRow row() {
        PresaleLlmPlatformConfigRow row = new PresaleLlmPlatformConfigRow();
        row.setPlatformCode("kimi");
        row.setApiUrl("https://api.example.com/v1");
        row.setModelId("model-x");
        row.setApiKey("ENC:abc");
        row.setPrimaryKeyRef("REF1");
        row.setMaxRetry(2);
        row.setTimeoutMs(10_000);
        row.setRateLimitQps(1000);
        row.setInWhitelist(1);
        return row;
    }
}
