package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.llm.LlmCallStatus;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmPlatformRouter;
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
        LlmInvoker llmInvoker = mock(LlmInvoker.class);
        ArticleModelResolver modelResolver = mock(ArticleModelResolver.class);
        LlmPlatformRouter router = mock(LlmPlatformRouter.class);
        ArticleAiDraftPromptFilter promptFilter = mock(ArticleAiDraftPromptFilter.class);
        BatchArticleQualityChecker qualityChecker = mock(BatchArticleQualityChecker.class);
        when(promptFilter.filterOutboundPrompt(any(), any(), any(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));
        when(promptFilter.filterGeneratedContent(any(), any(), any(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));
        LlmInvokeResult invokeResult = new LlmInvokeResult(
                "# Routed title\n\ncontent",
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
        when(router.invoke(any())).thenReturn(new LlmRouteResult(
                "qwen",
                "通义千问",
                "primary",
                "qwen-plus",
                "通义千问 Plus",
                invokeResult.responseText(),
                invokeResult.durationMs(),
                2,
                invokeResult
        ));

        ArticleGenerationEngine engine = new ArticleGenerationEngine(
                llmInvoker,
                modelResolver,
                router,
                mock(MarkdownImageReferenceValidator.class),
                promptFilter,
                qualityChecker
        );

        ArticleGenerationEngine.GeneratedArticle generated = engine.generate(new ArticleGenerationEngine.GenerateInput(
                new Project(),
                new Brand(),
                "system",
                "user",
                null,
                null,
                true,
                false,
                false,
                List.of()
        ));

        assertEquals("qwen", generated.model().platformCode());
        assertEquals("qwen-plus", generated.model().modelId());
        assertThat(generated.content()).contains("Routed title");
        verify(modelResolver, never()).resolve(any(), any(), any(), anyBoolean());
        verify(llmInvoker, never()).invoke(any(), any());
        ArgumentCaptor<LlmRouteRequest> requestCaptor = ArgumentCaptor.forClass(LlmRouteRequest.class);
        verify(router).invoke(requestCaptor.capture());
        assertEquals(LlmFeature.ARTICLE, requestCaptor.getValue().feature());
        assertEquals(0, requestCaptor.getValue().maxRetry());
    }
}
