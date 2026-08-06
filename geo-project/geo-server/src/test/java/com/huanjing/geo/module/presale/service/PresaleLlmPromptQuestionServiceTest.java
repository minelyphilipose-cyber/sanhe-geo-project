package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmCallResult;
import com.huanjing.geo.common.llm.LlmCallStatus;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.LlmProperties;
import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionGenerateRequest;
import com.huanjing.geo.module.presale.dto.response.LlmPromptQuestionGenerateVO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private LlmCallFacade llmCallFacade;
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
                llmCallFacade,
                new LlmProperties(),
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
        when(llmCallFacade.execute(any(LlmCallRequest.class)))
                .thenThrow(new com.huanjing.geo.common.llm.LlmInvokeException("HTTP 402: insufficient balance"))
                .thenReturn(LlmCallResult.direct(successResult("bbb")));

        LlmPromptQuestionGenerateVO result = service.generate(request());

        assertEquals(5, result.getGeneratedTotal());
        assertEquals(0, result.getMissingTotal());
        ArgumentCaptor<LlmCallRequest> configCaptor = ArgumentCaptor.forClass(LlmCallRequest.class);
        verify(llmCallFacade, org.mockito.Mockito.times(2)).execute(configCaptor.capture());
        assertEquals(List.of("http://first.example/v1", "http://second.example/v1"),
                configCaptor.getAllValues().stream().map(LlmCallRequest::modelConfig).map(LlmModelConfig::apiUrl).toList());
        assertEquals(List.of("low-model-aaa", "low-model-bbb"),
                configCaptor.getAllValues().stream().map(LlmCallRequest::modelConfig).map(LlmModelConfig::modelId).toList());
        assertEquals(List.of(30_000, 30_000),
                configCaptor.getAllValues().stream().map(LlmCallRequest::modelConfig).map(LlmModelConfig::requestTimeoutMs).toList());
    }

    @Test
    void generate_reportsQuotaMessageWhenAllPlatformsReturn402() throws Exception {
        AiPlatformConfig first = platform("aaa", "http://first.example/v1");
        when(aiPlatformConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first));
        when(platformCredentialService.resolveApiKey("aaa", null, "key-aaa")).thenReturn("key-aaa");
        when(llmCallFacade.execute(any(LlmCallRequest.class)))
                .thenThrow(new com.huanjing.geo.common.llm.LlmInvokeException("HTTP 402: insufficient balance"));

        BizException ex = assertThrows(BizException.class, () -> service.generate(request()));

        assertEquals("LLM 问题生成失败：AI 平台余额或额度不足，请检查平台账户", ex.getMessage());
    }

    @Test
    void generate_keepsProblemQuestionsThatMentionTargetBrandAndReturnsQualityErrors() throws Exception {
        AiPlatformConfig platform = platform("aaa", "http://first.example/v1");
        when(aiPlatformConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of(platform));
        when(platformCredentialService.resolveApiKey("aaa", null, "key-aaa")).thenReturn("key-aaa");
        when(llmCallFacade.execute(any(LlmCallRequest.class)))
                .thenReturn(LlmCallResult.direct(result("""
                        [
                          {"categoryCode":"PROBLEM","promptContent":"广州诗帝尼门窗有限公司售后靠不靠谱?"},
                          {"categoryCode":"PROBLEM","promptContent":"广州装修选门窗时售后和安装怎么避坑?"},
                          {"categoryCode":"COMPARISON","promptContent":"诗帝尼和 {competitor} 哪个更适合装修?"},
                          {"categoryCode":"COGNITIVE","promptContent":"诗帝尼门窗这个品牌怎么样?"},
                          {"categoryCode":"COGNITIVE","promptContent":"诗帝尼门窗质量口碑如何?"},
                          {"categoryCode":"COGNITIVE","promptContent":"诗帝尼在门窗行业知名度如何?"}
                        ]
                        """, "aaa")));

        LlmPromptQuestionGenerateVO result = service.generate(problemRequest());

        assertEquals(5, result.getGeneratedTotal());
        assertTrue(result.getQuestions().stream()
                .anyMatch(q -> q.getCategoryCode() == PresalePromptCategoryCode.PROBLEM
                        && q.getPromptContent().contains("广州诗帝尼门窗有限公司")
                        && q.getQualityErrors().stream().anyMatch(error -> error.contains("不能直接出现品牌"))));
        ArgumentCaptor<LlmCallRequest> promptCaptor = ArgumentCaptor.forClass(LlmCallRequest.class);
        verify(llmCallFacade).execute(promptCaptor.capture());
        assertTrue(promptCaptor.getValue().prompt().contains("问题型禁止出现客户品牌和代理品牌"));
    }

    @Test
    void generationPromptRequiresRecommendationAndScenarioToUseIndustryInsteadOfBrand() throws Exception {
        AiPlatformConfig platform = platform("aaa", "http://first.example/v1");
        when(aiPlatformConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of(platform));
        when(platformCredentialService.resolveApiKey("aaa", null, "key-aaa")).thenReturn("key-aaa");
        when(llmCallFacade.execute(any(LlmCallRequest.class))).thenReturn(LlmCallResult.direct(result("[]", "aaa")));

        try {
            service.generate(request());
        } catch (BizException ignored) {
            // 空结果会产生数量不足提示；本用例只校验提交给模型的规则。
        }

        ArgumentCaptor<LlmCallRequest> promptCaptor = ArgumentCaptor.forClass(LlmCallRequest.class);
        verify(llmCallFacade).execute(promptCaptor.capture());
        assertTrue(promptCaptor.getValue().prompt().contains("推荐型和场景型必须以行业、品类或用户需求为主体"));
        assertTrue(promptCaptor.getValue().prompt().contains("不得出现任何具体品牌名"));
    }

    private static LlmPromptQuestionGenerateRequest request() {
        Map<PresalePromptCategoryCode, Integer> counts = new EnumMap<>(PresalePromptCategoryCode.class);
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            counts.put(code, 0);
        }
        counts.put(PresalePromptCategoryCode.RECOMMENDATION, 1);
        counts.put(PresalePromptCategoryCode.COMPARISON, 1);
        counts.put(PresalePromptCategoryCode.COGNITIVE, 3);
        LlmPromptQuestionGenerateRequest request = new LlmPromptQuestionGenerateRequest();
        request.setBrandName("广州诗帝尼门窗有限公司");
        request.setIndustry("建筑装饰");
        request.setIndustryRole("service_provider");
        request.setRegion("全国");
        request.setUserType("装修客户");
        request.setUserDemand("了解品牌在 AI 搜索中的真实表现。");
        request.setTotalCount(5);
        request.setCategoryCounts(counts);
        request.setExistingQuestions(List.of());
        return request;
    }

    private static LlmPromptQuestionGenerateRequest problemRequest() {
        LlmPromptQuestionGenerateRequest request = request();
        Map<PresalePromptCategoryCode, Integer> counts = new EnumMap<>(PresalePromptCategoryCode.class);
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            counts.put(code, 0);
        }
        counts.put(PresalePromptCategoryCode.PROBLEM, 1);
        counts.put(PresalePromptCategoryCode.COMPARISON, 1);
        counts.put(PresalePromptCategoryCode.COGNITIVE, 3);
        request.setCategoryCounts(counts);
        request.setTotalCount(5);
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

    private static LlmInvokeResult successResult(String platformCode) {
        return result(
                "[{\"categoryCode\":\"RECOMMENDATION\",\"promptContent\":\"广州门窗品牌哪家值得推荐?\"},"
                        + "{\"categoryCode\":\"COMPARISON\",\"promptContent\":\"诗帝尼和 {competitor} 哪个更适合装修?\"},"
                        + "{\"categoryCode\":\"COGNITIVE\",\"promptContent\":\"诗帝尼门窗这个品牌怎么样?\"},"
                        + "{\"categoryCode\":\"COGNITIVE\",\"promptContent\":\"诗帝尼门窗质量口碑如何?\"},"
                        + "{\"categoryCode\":\"COGNITIVE\",\"promptContent\":\"诗帝尼在门窗行业知名度如何?\"}]",
                platformCode
        );
    }

    private static LlmInvokeResult result(String responseText, String platformCode) {
        return new LlmInvokeResult(
                responseText,
                10,
                20,
                120L,
                0,
                LlmCallStatus.SUCCESS,
                platformCode,
                platformCode,
                "low-model-" + platformCode,
                "low-model-" + platformCode
        );
    }
}
