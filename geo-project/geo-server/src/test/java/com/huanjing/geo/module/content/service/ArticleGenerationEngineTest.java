package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.LlmCallStatus;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmCallResult;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.measurement.LlmCallMeasurementContext;
import com.huanjing.geo.common.llm.measurement.LlmObservationScope;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleGenerationEngineTest {

    @Test
    void generateUsesRouterWhenNoModelSpecified() throws Exception {
        LlmCallFacade llmCallFacade = mock(LlmCallFacade.class);
        ArticleModelResolver modelResolver = mock(ArticleModelResolver.class);
        ArticleAiDraftPromptFilter promptFilter = mock(ArticleAiDraftPromptFilter.class);
        BatchArticleQualityChecker qualityChecker = new BatchArticleQualityChecker(new ObjectMapper());
        when(promptFilter.filterOutboundPrompt(any(), any(), any(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));
        when(promptFilter.filterGeneratedContent(any(), any(), any(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));
        LlmInvokeResult invokeResult = new LlmInvokeResult(
                "# 这是一个为了验证自媒体平台标题长度限制而特意生成的超长文章标题\n\ncontent",
                10,
                20,
                100L,
                0,
                LlmCallStatus.SUCCESS,
                "qwen",
                "通义千问",
                "qwen-plus",
                "通义千问 Plus"
        );
        when(llmCallFacade.execute(any())).thenReturn(LlmCallResult.routed(new LlmRouteResult(
                "qwen",
                "通义千问",
                "primary",
                "qwen-plus",
                "通义千问 Plus",
                invokeResult.responseText(),
                invokeResult.durationMs(),
                2,
                invokeResult
        )));

        ArticleGenerationEngine engine = new ArticleGenerationEngine(
                llmCallFacade,
                modelResolver,
                mock(MarkdownImageReferenceValidator.class),
                promptFilter,
                qualityChecker,
                mock(ArticleTitleDuplicateChecker.class)
        );

        Project project = new Project();
        project.setId(1L);
        LlmCallMeasurementContext measurementContext = new LlmCallMeasurementContext(
                "article-batch:1:task:2:infra:0:attempt:1",
                null,
                1L,
                LlmObservationScope.PROJECT,
                null
        );
        ArticleGenerationEngine.GeneratedArticle generated = engine.generate(new ArticleGenerationEngine.GenerateInput(
                project,
                new Brand(),
                "system",
                "user",
                null,
                null,
                true,
                false,
                true,
                List.of(),
                28,
                ArticleGenerationTemperatures.V2_STANDARD,
                measurementContext
        ));

        assertEquals("qwen", generated.model().platformCode());
        assertEquals("qwen-plus", generated.model().modelId());
        assertThat(generated.title().codePointCount(0, generated.title().length())).isLessThanOrEqualTo(28);
        assertThat(generated.content()).startsWith("# " + generated.title());
        assertThat(generated.quality().issues())
                .extracting(BatchArticleQualityChecker.Issue::type)
                .contains("title_truncated");
        verify(modelResolver, never()).resolve(any(), any(), any(), anyBoolean());
        ArgumentCaptor<LlmCallRequest> requestCaptor = ArgumentCaptor.forClass(LlmCallRequest.class);
        verify(llmCallFacade).execute(requestCaptor.capture());
        assertEquals(measurementContext, requestCaptor.getValue().measurementContext());
        LlmRouteRequest routeRequest = requestCaptor.getValue().routeRequest();
        assertEquals(LlmFeature.ARTICLE, routeRequest.feature());
        assertEquals(LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS, routeRequest.requestTimeoutMs());
        assertEquals(LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS, routeRequest.requestTimeoutMaxMs());
        assertEquals(0, routeRequest.maxRetry());
        assertEquals(ArticleGenerationTemperatures.V2_STANDARD, routeRequest.temperature());
    }
}
