package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.pool.LlmPermitScope;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.LlmInvokeException;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptJudgeResult;
import com.huanjing.geo.module.presale.persist.mapper.IndustryCoreAttributeConfigMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptJudgeResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PresaleJudgeServiceTest {

    @Mock
    private PresaleAiPromptResultMapper promptResultMapper;
    @Mock
    private PresaleAiPromptJudgeResultMapper judgeResultMapper;
    @Mock
    private IndustryCoreAttributeConfigMapper attributeConfigMapper;
    @Mock
    private PresaleReportVersionMapper reportVersionMapper;
    @Mock
    private PresaleReportMapper reportMapper;
    @Mock
    private PresaleLlmInvoker llmInvoker;
    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private PresaleEvaluationModelRouter evaluationModelRouter;

    private PresaleJudgeService judgeService;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        judgeService = new PresaleJudgeService(
                promptResultMapper,
                judgeResultMapper,
                attributeConfigMapper,
                reportVersionMapper,
                reportMapper,
                llmInvoker,
                aiPlatformConfigMapper,
                evaluationModelRouter,
                new ObjectMapper(),
                directExecutor
        );
        ReflectionTestUtils.setField(judgeService, "judgeMaxAttempts", 2);
        ReflectionTestUtils.setField(judgeService, "judgeTemperature", 0D);
        ReflectionTestUtils.setField(judgeService, "permitBusyBackoffMs", 0L);

        AiPlatformConfig config = new AiPlatformConfig();
        config.setPlatformCode("kimi");
        config.setLowModelId("test-low-model");
        lenient().when(aiPlatformConfigMapper.selectOne(any())).thenReturn(config);
        lenient().when(evaluationModelRouter.routeContexts(any()))
                .thenReturn(List.of(new PlatformCallContext(1L, 1, "deepseek", 101L, "", "目标品牌", 100L, true)));
        lenient().when(judgeResultMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void shouldPersistCognitiveAsEnglishCategoryAndRecomputeSentiment() throws Exception {
        PresaleJudgeCandidateRow row = baseCandidate();
        row.setBatchNo(1);
        row.setCategory("认知型");
        when(promptResultMapper.selectJudgeCandidatesByVersionAndCategory(1L, 1, "认知型"))
                .thenReturn(List.of(row));
        when(llmInvoker.judge(any(), anyString(), anyDouble()))
                .thenReturn(new LlmCallResult(
                        "{\"sentiment\":\"NEGATIVE\",\"sentiment_score\":0.8,\"attributes_hit\":[\"专业\"],\"factual_errors\":[],\"tone\":\"OBJECTIVE\"}",
                        10, 20, 100L, 0, com.huanjing.geo.module.presale.generate.llm.CallStatus.SUCCESS
                ));

        judgeService.judgeCognitiveAfterBatch1(1L, "目标品牌", 100L, true);

        ArgumentCaptor<PresaleAiPromptJudgeResult> captor = ArgumentCaptor.forClass(PresaleAiPromptJudgeResult.class);
        verify(judgeResultMapper, times(1)).upsertByPromptResultId(captor.capture());
        PresaleAiPromptJudgeResult saved = captor.getValue();
        assertEquals("COGNITIVE", saved.getCategory());
        assertEquals("POSITIVE", saved.getSentiment());
        assertEquals(new BigDecimal("0.8000"), saved.getSentimentScore());
        assertEquals("SUCCESS", saved.getJudgeStatus());
    }

    @Test
    void shouldPersistComparisonAsEnglishCategory() throws Exception {
        PresaleJudgeCandidateRow row = baseCandidate();
        row.setBatchNo(2);
        row.setCategory("对比型");
        row.setCompetitorName("竞品A");
        when(promptResultMapper.selectJudgeCandidatesByVersionAndCategory(1L, 2, "对比型"))
                .thenReturn(List.of(row));
        when(llmInvoker.judge(any(), anyString(), anyDouble()))
                .thenReturn(new LlmCallResult(
                        "{\"verdicts\":[{\"competitor\":\"竞品A\",\"preferred_brand\":\"target\",\"target_sentiment\":\"POSITIVE\",\"target_advantages\":[],\"target_disadvantages\":[],\"competitor_advantages\":[],\"reasoning_quality\":\"high\"}]}",
                        10, 20, 100L, 0, com.huanjing.geo.module.presale.generate.llm.CallStatus.SUCCESS
                ));

        judgeService.judgeComparisonAfterBatch2(1L, "目标品牌", 100L, true);

        ArgumentCaptor<PresaleAiPromptJudgeResult> captor = ArgumentCaptor.forClass(PresaleAiPromptJudgeResult.class);
        verify(judgeResultMapper, times(1)).upsertByPromptResultId(captor.capture());
        PresaleAiPromptJudgeResult saved = captor.getValue();
        assertEquals("COMPARISON", saved.getCategory());
        assertEquals("target", saved.getPreferredBrand());
        assertEquals("SUCCESS", saved.getJudgeStatus());
    }

    @Test
    void shouldNotOuterRetryWhenLlmCallFailed() throws Exception {
        PresaleJudgeCandidateRow row = baseCandidate();
        row.setBatchNo(1);
        row.setCategory("认知型");
        when(promptResultMapper.selectJudgeCandidatesByVersionAndCategory(1L, 1, "认知型"))
                .thenReturn(List.of(row));
        when(llmInvoker.judge(any(), anyString(), anyDouble()))
                .thenThrow(new LlmInvokeException("network timeout"));

        judgeService.judgeCognitiveAfterBatch1(1L, "目标品牌", 100L, true);

        verify(llmInvoker, times(1)).judge(any(), anyString(), anyDouble());
        ArgumentCaptor<PresaleAiPromptJudgeResult> captor = ArgumentCaptor.forClass(PresaleAiPromptJudgeResult.class);
        verify(judgeResultMapper, times(1)).upsertByPromptResultId(captor.capture());
        PresaleAiPromptJudgeResult saved = captor.getValue();
        assertEquals("FAILED", saved.getJudgeStatus());
        assertEquals(1, saved.getJudgeAttemptCount());
        assertNotNull(saved.getJudgeError());
    }

    @Test
    void shouldOuterRetryWhenEvaluationPermitBusy() throws Exception {
        PresaleJudgeCandidateRow row = baseCandidate();
        row.setBatchNo(1);
        row.setCategory("认知型");
        when(promptResultMapper.selectJudgeCandidatesByVersionAndCategory(1L, 1, "认知型"))
                .thenReturn(List.of(row));
        when(llmInvoker.judge(any(), anyString(), anyDouble()))
                .thenThrow(new LlmPermitUnavailableException(LlmPermitScope.PLATFORM, "deepseek"));

        judgeService.judgeCognitiveAfterBatch1(1L, "目标品牌", 100L, true);

        verify(llmInvoker, times(2)).judge(any(), anyString(), anyDouble());
        ArgumentCaptor<PresaleAiPromptJudgeResult> captor = ArgumentCaptor.forClass(PresaleAiPromptJudgeResult.class);
        verify(judgeResultMapper, times(1)).upsertByPromptResultId(captor.capture());
        PresaleAiPromptJudgeResult saved = captor.getValue();
        assertEquals("FAILED", saved.getJudgeStatus());
        assertEquals(2, saved.getJudgeAttemptCount());
        assertNotNull(saved.getJudgeError());
    }

    @Test
    void shouldPassClientIndustryAndRoleToCognitiveJudge() throws Exception {
        PresaleJudgeCandidateRow row = baseCandidate();
        row.setBatchNo(1);
        row.setCategory("\u8ba4\u77e5\u578b");
        when(promptResultMapper.selectJudgeCandidatesByVersionAndCategory(1L, 1, "\u8ba4\u77e5\u578b"))
                .thenReturn(List.of(row));
        when(llmInvoker.judge(any(), anyString(), anyDouble()))
                .thenReturn(new LlmCallResult(
                        "{\"sentiment\":\"NEUTRAL\",\"sentiment_score\":0,\"attributes_hit\":[],\"factual_errors\":[],\"tone\":\"OBJECTIVE\"}",
                        10, 20, 100L, 0, com.huanjing.geo.module.presale.generate.llm.CallStatus.SUCCESS
                ));

        judgeService.judgeCognitiveAfterBatch1(
                1L, "\u76ee\u6807\u54c1\u724c", "\u6c7d\u8f66\u670d\u52a1",
                "\u6388\u6743\u7ecf\u9500\u5546", List.of("\u5b9d\u9a6c", "MINI"), 100L, true, null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmInvoker).judge(any(), promptCaptor.capture(), anyDouble());
        assertTrue(promptCaptor.getValue().contains("\u5ba2\u6237\u884c\u4e1a:\u6c7d\u8f66\u670d\u52a1"));
        assertTrue(promptCaptor.getValue().contains("\u5ba2\u6237\u8eab\u4efd:\u6388\u6743\u7ecf\u9500\u5546"));
        assertTrue(promptCaptor.getValue().contains("\u4ee3\u7406\u54c1\u724c:\u5b9d\u9a6c\u3001MINI"));
        assertTrue(promptCaptor.getValue().contains("\u4e0a\u6e38\u54c1\u724c"));
    }

    @Test
    void shouldPassClientIndustryAndRoleToComparisonJudge() throws Exception {
        PresaleJudgeCandidateRow row = baseCandidate();
        row.setBatchNo(2);
        row.setCategory("\u5bf9\u6bd4\u578b");
        row.setCompetitorName("\u7ade\u54c1A");
        when(promptResultMapper.selectJudgeCandidatesByVersionAndCategory(1L, 2, "\u5bf9\u6bd4\u578b"))
                .thenReturn(List.of(row));
        when(llmInvoker.judge(any(), anyString(), anyDouble()))
                .thenReturn(new LlmCallResult(
                        "{\"verdicts\":[{\"competitor\":\"\u7ade\u54c1A\",\"preferred_brand\":\"tie\",\"target_sentiment\":\"NEUTRAL\",\"target_advantages\":[],\"target_disadvantages\":[],\"competitor_advantages\":[],\"reasoning_quality\":\"medium\"}]}",
                        10, 20, 100L, 0, com.huanjing.geo.module.presale.generate.llm.CallStatus.SUCCESS
                ));

        judgeService.judgeComparisonAfterBatch2(
                1L, "\u76ee\u6807\u54c1\u724c", "\u6c7d\u8f66\u670d\u52a1",
                "\u6388\u6743\u7ecf\u9500\u5546", List.of("\u5b9d\u9a6c", "MINI"), 100L, true, null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmInvoker).judge(any(), promptCaptor.capture(), anyDouble());
        assertTrue(promptCaptor.getValue().contains("\u5ba2\u6237\u884c\u4e1a:\u6c7d\u8f66\u670d\u52a1"));
        assertTrue(promptCaptor.getValue().contains("\u5ba2\u6237\u8eab\u4efd:\u6388\u6743\u7ecf\u9500\u5546"));
        assertTrue(promptCaptor.getValue().contains("\u4ee3\u7406\u54c1\u724c:\u5b9d\u9a6c\u3001MINI"));
        assertTrue(promptCaptor.getValue().contains("\u4e0d\u5f97\u5c06\u4ee3\u7406\u54c1\u724c\u4f18\u52bf"));
    }

    private PresaleJudgeCandidateRow baseCandidate() {
        PresaleJudgeCandidateRow row = new PresaleJudgeCandidateRow();
        row.setPromptResultId(11L);
        row.setVersionId(1L);
        row.setPlatformCode("kimi");
        row.setPromptTemplateId(101L);
        row.setRequestPromptContent("prompt");
        row.setQueryAnswer("answer");
        row.setCompetitorName("");
        return row;
    }
}
