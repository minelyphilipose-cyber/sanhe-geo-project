package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionGenerateRequest;
import com.huanjing.geo.module.presale.dto.response.LlmPromptQuestionGenerateVO;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmHttpClient;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleLlmPromptQuestionServiceTest {

    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private PlatformCredentialService platformCredentialService;
    @Mock
    private PresaleLlmHttpClient httpClient;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PresaleLlmQuestionRateLimiter rateLimiter;

    private PresaleLlmPromptQuestionService service;

    @BeforeEach
    void setUp() {
        service = new PresaleLlmPromptQuestionService(
                aiPlatformConfigMapper,
                platformCredentialService,
                httpClient,
                new ObjectMapper(),
                currentUserService,
                rateLimiter,
                new LlmPromptQuestionDraftValidator()
        );
        SysUser user = new SysUser();
        user.setId(5L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);
    }

    @Test
    void generate_fallsBackToNextPlatformWhenFirstReturns402() throws Exception {
        AiPlatformConfig first = platform("aaa", "http://first.example/v1");
        AiPlatformConfig second = platform("bbb", "http://second.example/v1");
        when(aiPlatformConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        when(platformCredentialService.resolveApiKey("aaa", null, "key-aaa")).thenReturn("key-aaa");
        when(platformCredentialService.resolveApiKey("bbb", null, "key-bbb")).thenReturn("key-bbb");
        when(httpClient.postJson(anyString(), anyMap(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PresaleLlmHttpClient.HttpResponse(402, "{\"error\":\"insufficient balance\"}"))
                .thenReturn(new PresaleLlmHttpClient.HttpResponse(200, successBody()));

        LlmPromptQuestionGenerateVO result = service.generate(request());

        assertEquals(2, result.getGeneratedTotal());
        assertEquals(0, result.getMissingTotal());
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> requestTimeoutCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(httpClient, org.mockito.Mockito.times(2))
                .postJson(urlCaptor.capture(), anyMap(), bodyCaptor.capture(), anyInt(), requestTimeoutCaptor.capture());
        assertEquals(List.of("http://first.example/v1/chat/completions", "http://second.example/v1/chat/completions"),
                urlCaptor.getAllValues());
        assertEquals(true, bodyCaptor.getAllValues().get(0).contains("\"model\":\"low-model-aaa\""));
        assertEquals(true, bodyCaptor.getAllValues().get(1).contains("\"model\":\"low-model-bbb\""));
        assertEquals(List.of(60000, 60000), requestTimeoutCaptor.getAllValues());
    }

    @Test
    void generate_reportsQuotaMessageWhenAllPlatformsReturn402() throws Exception {
        AiPlatformConfig first = platform("aaa", "http://first.example/v1");
        when(aiPlatformConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first));
        when(platformCredentialService.resolveApiKey("aaa", null, "key-aaa")).thenReturn("key-aaa");
        when(httpClient.postJson(anyString(), anyMap(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PresaleLlmHttpClient.HttpResponse(402, "{\"error\":\"insufficient balance\"}"));

        BizException ex = assertThrows(BizException.class, () -> service.generate(request()));

        assertEquals("LLM 问题生成失败：AI 平台余额或额度不足，请检查平台账户", ex.getMessage());
    }

    private static LlmPromptQuestionGenerateRequest request() {
        Map<PresalePromptCategoryCode, Integer> counts = new EnumMap<>(PresalePromptCategoryCode.class);
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            counts.put(code, 0);
        }
        counts.put(PresalePromptCategoryCode.RECOMMENDATION, 1);
        counts.put(PresalePromptCategoryCode.COMPARISON, 1);
        LlmPromptQuestionGenerateRequest request = new LlmPromptQuestionGenerateRequest();
        request.setBrandName("广州诗帝尼门窗有限公司");
        request.setIndustry("建筑装饰");
        request.setIndustryRole("service_provider");
        request.setRegion("全国");
        request.setUserType("装修客户");
        request.setUserDemand("了解品牌在 AI 搜索中的真实表现。");
        request.setTotalCount(2);
        request.setCategoryCounts(counts);
        request.setExistingQuestions(List.of());
        return request;
    }

    private static AiPlatformConfig platform(String code, String apiUrl) {
        AiPlatformConfig config = new AiPlatformConfig();
        config.setPlatformCode(code);
        config.setApiUrl(apiUrl);
        config.setApiKey("key-" + code);
        config.setModelId("high-model-" + code);
        config.setLowModelId("low-model-" + code);
        return config;
    }

    private static String successBody() {
        return """
                {"choices":[{"message":{"content":"[{\\\"categoryCode\\\":\\\"RECOMMENDATION\\\",\\\"promptContent\\\":\\\"广州门窗品牌哪家值得推荐?\\\"},{\\\"categoryCode\\\":\\\"COMPARISON\\\",\\\"promptContent\\\":\\\"诗帝尼和 {competitor} 哪个更适合装修?\\\"}]"}}]}
                """;
    }
}
