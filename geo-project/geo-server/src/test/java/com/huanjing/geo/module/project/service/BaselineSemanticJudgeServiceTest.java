package com.huanjing.geo.module.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmCallResult;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.common.llm.router.LlmPlatformRouter;
import com.huanjing.geo.module.project.entity.BaselineQuestionSnapshot;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.presale.generate.PresaleEvaluationModelRouter;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaselineSemanticJudgeServiceTest {

    @Test
    void judge_usesBaselineFeatureAndMergesSemanticHits() {
        LlmPlatformRouter router = mock(LlmPlatformRouter.class);
        PresaleEvaluationModelRouter evaluationModelRouter = mock(PresaleEvaluationModelRouter.class);
        when(evaluationModelRouter.routePlatforms()).thenReturn(List.of(platform()));
        BaselineSemanticJudgeService service = service(router, evaluationModelRouter);
        when(router.invoke(any())).thenReturn(new LlmRouteResult(
                "doubao",
                "豆包",
                "primary",
                "low-model",
                "低性能模型",
                """
                        {
                          "brand_mentioned": true,
                          "brand_mention_type": "BRAND_ALIAS",
                          "brand_evidence_text": "鸿蒙智家",
                          "recommended": true,
                          "ranking_position": 1,
                          "sentiment": "POSITIVE",
                          "impression_state": "POSITIVE",
                          "judge_evidence": "回答把鸿蒙智家列为推荐选项",
                          "competitor_mentions": [
                            {
                              "canonical_name": "米家",
                              "mention_type": "COMPETITOR_EXACT",
                              "evidence_text": "米家"
                            }
                          ],
                          "negative_evidence_texts": []
                        }
                        """,
                1200,
                1,
                null
        ));

        BaselineSemanticJudgeResult result = service.judge(
                question("RECOMMENDATION"),
                project(),
                List.of("鸿蒙智家"),
                List.of(new BaselineObservationScoringRules.CompetitorName(7L, "米家", List.of("小米米家"), true)),
                platform(),
                "推荐鸿蒙智家, 也可以对比米家。"
        );

        assertThat(result.isJudgeUsed()).isTrue();
        assertThat(result.isMentioned()).isTrue();
        assertThat(result.isRecommended()).isTrue();
        assertThat(result.getSentiment()).isEqualTo("POSITIVE");
        assertThat(result.getBrandHit().getRawText()).isEqualTo("鸿蒙智家");
        assertThat(result.getCompetitorHits()).hasSize(1);
        assertThat(result.getCompetitorHits().get(0).getEntityId()).isEqualTo(7L);
        assertThat(result.getCompetitorHits().get(0).getStartOffset()).isNotNull();

        ArgumentCaptor<LlmRouteRequest> captor = ArgumentCaptor.forClass(LlmRouteRequest.class);
        verify(router).invoke(captor.capture());
        assertThat(captor.getValue().feature()).isEqualTo(LlmFeature.BASELINE);
        assertThat(captor.getValue().platformConfigs()).hasSize(1);
        assertThat(captor.getValue().platformConfigs().get(0).getModelId()).isEqualTo("low-model");
    }

    @Test
    void judge_forcesUnknownSentimentWhenBrandNotMentioned() {
        LlmPlatformRouter router = mock(LlmPlatformRouter.class);
        PresaleEvaluationModelRouter evaluationModelRouter = mock(PresaleEvaluationModelRouter.class);
        when(evaluationModelRouter.routePlatforms()).thenReturn(List.of(platform()));
        BaselineSemanticJudgeService service = service(router, evaluationModelRouter);
        when(router.invoke(any())).thenReturn(new LlmRouteResult(
                "doubao",
                "豆包",
                "primary",
                "low-model",
                "低性能模型",
                """
                        {
                          "brand_mentioned": false,
                          "brand_mention_type": "NONE",
                          "brand_evidence_text": null,
                          "recommended": true,
                          "ranking_position": 1,
                          "sentiment": "POSITIVE",
                          "impression_state": "INFO_MISSING",
                          "judge_evidence": "未提及目标品牌",
                          "competitor_mentions": [],
                          "negative_evidence_texts": []
                        }
                        """,
                900,
                1,
                null
        ));

        BaselineSemanticJudgeResult result = service.judge(
                question("RECOMMENDATION"),
                project(),
                List.of("鸿蒙智家"),
                List.of(),
                platform(),
                "米家和智和家在当地更常被推荐。"
        );

        assertThat(result.isMentioned()).isFalse();
        assertThat(result.isRecommended()).isFalse();
        assertThat(result.getRankingPosition()).isNull();
        assertThat(result.getSentiment()).isEqualTo("UNKNOWN");
    }

    @Test
    void judge_downgradesNegativeWithoutTraceableEvidence() {
        LlmPlatformRouter router = mock(LlmPlatformRouter.class);
        PresaleEvaluationModelRouter evaluationModelRouter = mock(PresaleEvaluationModelRouter.class);
        when(evaluationModelRouter.routePlatforms()).thenReturn(List.of(platform()));
        BaselineSemanticJudgeService service = service(router, evaluationModelRouter);
        when(router.invoke(any())).thenReturn(new LlmRouteResult(
                "doubao",
                "豆包",
                "primary",
                "low-model",
                "低性能模型",
                """
                        {
                          "brand_mentioned": true,
                          "brand_mention_type": "BRAND_EXACT",
                          "brand_evidence_text": "华为鸿蒙智家",
                          "recommended": false,
                          "ranking_position": null,
                          "sentiment": "NEGATIVE",
                          "impression_state": "NEGATIVE",
                          "judge_evidence": "误判为负面",
                          "competitor_mentions": [],
                          "negative_evidence_texts": []
                        }
                        """,
                900,
                1,
                null
        ));

        BaselineSemanticJudgeResult result = service.judge(
                question("PROBLEM"),
                project(),
                List.of("鸿蒙智家"),
                List.of(),
                platform(),
                "华为鸿蒙智家可为用户遇到问题时提供售后支持。"
        );

        assertThat(result.getSentiment()).isEqualTo("NEUTRAL");
        assertThat(result.getImpressionState()).isEqualTo("NEUTRAL");
        assertThat(result.getNegativeHits()).isEmpty();
    }

    @Test
    void ruleFallback_doesNotTreatGenericProblemAsNegative() {
        LlmPlatformRouter router = mock(LlmPlatformRouter.class);
        PresaleEvaluationModelRouter evaluationModelRouter = mock(PresaleEvaluationModelRouter.class);
        when(evaluationModelRouter.routePlatforms()).thenReturn(List.of());
        BaselineSemanticJudgeService service = service(router, evaluationModelRouter);

        BaselineSemanticJudgeResult result = service.judge(
                question("PROBLEM"),
                project(),
                List.of("鸿蒙智家"),
                List.of(),
                platform(),
                "华为鸿蒙智家可为用户遇到问题时提供售后支持。"
        );

        assertThat(result.getSentiment()).isEqualTo("NEUTRAL");
        assertThat(result.getNegativeHits()).isEmpty();
    }

    @Test
    void judge_fallsBackWhenPresaleEvaluationPoolDisabled() {
        LlmPlatformRouter router = mock(LlmPlatformRouter.class);
        PresaleEvaluationModelRouter evaluationModelRouter = mock(PresaleEvaluationModelRouter.class);
        when(evaluationModelRouter.routePlatforms()).thenReturn(List.of());
        BaselineSemanticJudgeService service = service(router, evaluationModelRouter);

        BaselineSemanticJudgeResult result = service.judge(
                question("RECOMMENDATION"),
                project(),
                List.of("鸿蒙智家"),
                List.of(),
                platform(),
                "推荐鸿蒙智家。"
        );

        assertThat(result.isJudgeUsed()).isFalse();
        assertThat(result.isMentioned()).isTrue();
    }

    private BaselineSemanticJudgeService service(LlmPlatformRouter router,
                                                 PresaleEvaluationModelRouter evaluationModelRouter) {
        LlmCallFacade facade = mock(LlmCallFacade.class);
        try {
            org.mockito.Mockito.when(facade.execute(any(LlmCallRequest.class))).thenAnswer(invocation -> {
                LlmCallRequest request = invocation.getArgument(0);
                return LlmCallResult.routed(router.invoke(request.routeRequest()));
            });
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return new BaselineSemanticJudgeService(facade, evaluationModelRouter, new ObjectMapper());
    }

    private BaselineQuestionSnapshot question(String intentType) {
        BaselineQuestionSnapshot question = new BaselineQuestionSnapshot();
        question.setId(1L);
        question.setQuestionText("阜阳全屋智能哪家好");
        question.setIntentType(intentType);
        return question;
    }

    private Project project() {
        Project project = new Project();
        project.setBrandName("华为鸿蒙智家");
        return project;
    }

    private AiPlatformConfig platform() {
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setPlatformCode("doubao");
        platform.setPlatformName("豆包");
        platform.setModelId("high-model");
        platform.setLowModelId("low-model");
        return platform;
    }
}
