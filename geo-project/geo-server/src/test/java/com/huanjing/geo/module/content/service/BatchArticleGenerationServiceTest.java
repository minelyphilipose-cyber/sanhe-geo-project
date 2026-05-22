package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.ArticleGenerationLogMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateVersionMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationBatchMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchArticleGenerationServiceTest {

    private ArticlePromptTemplateMapper promptTemplateMapper;
    private ArticlePromptTemplateVersionMapper promptTemplateVersionMapper;
    private BatchArticlePromptBuilder promptBuilder;
    private BatchArticleGenerationService service;

    @BeforeEach
    void setUp() {
        promptTemplateMapper = mock(ArticlePromptTemplateMapper.class);
        promptTemplateVersionMapper = mock(ArticlePromptTemplateVersionMapper.class);
        promptBuilder = mock(BatchArticlePromptBuilder.class);
        service = new BatchArticleGenerationService(
                mock(ProjectMapper.class),
                mock(BrandMapper.class),
                mock(KeywordGroupMapper.class),
                mock(KeywordGroupResultMapper.class),
                mock(ProjectKeywordGroupRelMapper.class),
                mock(AiPlatformConfigMapper.class),
                mock(ArticleDraftMapper.class),
                mock(ArticleDraftVersionMapper.class),
                mock(ArticleGenerationLogMapper.class),
                promptTemplateMapper,
                promptTemplateVersionMapper,
                mock(BatchArticleGenerationBatchMapper.class),
                mock(BatchArticleGenerationTaskMapper.class),
                mock(CurrentUserService.class),
                mock(com.huanjing.geo.module.customer.access.BrandAccessService.class),
                mock(PlatformCredentialService.class),
                mock(LlmInvoker.class),
                mock(MarkdownImageReferenceValidator.class),
                mock(ArticleAiDraftPromptFilter.class),
                promptBuilder,
                mock(BatchArticleQualityChecker.class),
                mock(ArticleTemplateAllocationService.class),
                new QuestionScenePlatformSuggestionService(),
                new ObjectMapper(),
                mock(PlatformTransactionManager.class),
                (Executor) Runnable::run
        );
    }

    @Test
    void buildPromptMarksFallbackSourceWhenTaskHasNoTemplateSnapshot() {
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setTemplateSource("weighted");
        BatchArticlePromptBuilder.PromptBuildInput input = promptInput();
        BatchArticlePromptBuilder.PromptBuildResult defaultResult = promptResult();
        when(promptBuilder.build(input)).thenReturn(defaultResult);

        BatchArticlePromptBuilder.PromptBuildResult result = ReflectionTestUtils.invokeMethod(
                service, "buildPrompt", task, input);

        assertSame(defaultResult, result);
        assertEquals("fallback_default_prompt", task.getTemplateSource());
        verify(promptBuilder).build(input);
        verify(promptTemplateMapper, never()).selectById(any());
        verify(promptTemplateVersionMapper, never()).selectById(any());
    }

    @Test
    void buildPromptKeepsFallbackBehaviorAndMarksSourceWhenTemplateSnapshotMissing() {
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(91L);
        task.setPromptTemplateId(11L);
        task.setPromptTemplateVersionId(12L);
        task.setTemplateSource("smart");
        BatchArticlePromptBuilder.PromptBuildInput input = promptInput();
        BatchArticlePromptBuilder.PromptBuildResult defaultResult = promptResult();
        when(promptTemplateMapper.selectById(11L)).thenReturn(null);
        when(promptTemplateVersionMapper.selectById(12L)).thenReturn(null);
        when(promptBuilder.build(input)).thenReturn(defaultResult);

        BatchArticlePromptBuilder.PromptBuildResult result = ReflectionTestUtils.invokeMethod(
                service, "buildPrompt", task, input);

        assertSame(defaultResult, result);
        assertEquals("fallback_default_prompt", task.getTemplateSource());
        verify(promptBuilder).build(input);
    }

    private BatchArticlePromptBuilder.PromptBuildInput promptInput() {
        Project project = new Project();
        project.setId(1L);
        project.setProjectName("Project");
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("Brand");
        return new BatchArticlePromptBuilder.PromptBuildInput(
                project,
                brand,
                "brand statement",
                "manual",
                "topic",
                "question",
                null,
                null,
                List.of(),
                "buying_guide",
                "zhihu",
                "medium",
                null,
                1,
                List.of(),
                null
        );
    }

    private BatchArticlePromptBuilder.PromptBuildResult promptResult() {
        return new BatchArticlePromptBuilder.PromptBuildResult(
                "system",
                "user",
                "angle",
                "audience",
                "{}",
                "{}"
        );
    }
}
