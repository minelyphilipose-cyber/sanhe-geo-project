package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateResponse;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationBatch;
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
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchArticleGenerationServiceTest {

    private ArticlePromptTemplateMapper promptTemplateMapper;
    private ArticlePromptTemplateVersionMapper promptTemplateVersionMapper;
    private ProjectMapper projectMapper;
    private BatchArticleGenerationBatchMapper batchMapper;
    private BatchArticleGenerationTaskMapper taskMapper;
    private BatchArticlePromptBuilder promptBuilder;
    private ArticleGenerationPromptContextFactory promptContextFactory;
    private ArticleTemplateAllocationService allocationService;
    private TemplatePerspectiveService perspectiveService;
    private BatchArticleGenerationService service;

    @BeforeEach
    void setUp() {
        promptTemplateMapper = mock(ArticlePromptTemplateMapper.class);
        promptTemplateVersionMapper = mock(ArticlePromptTemplateVersionMapper.class);
        projectMapper = mock(ProjectMapper.class);
        batchMapper = mock(BatchArticleGenerationBatchMapper.class);
        taskMapper = mock(BatchArticleGenerationTaskMapper.class);
        promptBuilder = mock(BatchArticlePromptBuilder.class);
        promptContextFactory = mock(ArticleGenerationPromptContextFactory.class);
        allocationService = mock(ArticleTemplateAllocationService.class);
        perspectiveService = mock(TemplatePerspectiveService.class);
        service = new BatchArticleGenerationService(
                projectMapper,
                mock(BrandMapper.class),
                mock(KeywordGroupMapper.class),
                mock(KeywordGroupResultMapper.class),
                mock(ProjectKeywordGroupRelMapper.class),
                mock(ArticleDraftMapper.class),
                mock(ArticleDraftVersionMapper.class),
                mock(ArticleGenerationLogMapper.class),
                promptTemplateMapper,
                promptTemplateVersionMapper,
                batchMapper,
                taskMapper,
                mock(CurrentUserService.class),
                mock(com.huanjing.geo.module.customer.access.BrandAccessService.class),
                mock(PlatformCredentialService.class),
                mock(LlmInvoker.class),
                mock(MarkdownImageReferenceValidator.class),
                mock(ArticleAiDraftPromptFilter.class),
                mock(ArticleGenerationEngine.class),
                mock(ArticleModelResolver.class),
                passThroughAutoImageInsertionService(),
                promptBuilder,
                promptContextFactory,
                mock(BatchArticleQualityChecker.class),
                allocationService,
                perspectiveService,
                new QuestionScenePlatformSuggestionService(),
                mock(ArticleGenerationReadinessService.class),
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

    @Test
    void runTaskMarksOnlyCurrentTaskFailedWhenPromptRenderThrows() {
        Project project = new Project();
        project.setId(1L);
        project.setProjectName("Project");
        project.setStatus("active");
        when(projectMapper.selectById(1L)).thenReturn(project);

        BatchArticleGenerationBatch batch = new BatchArticleGenerationBatch();
        batch.setId(10L);
        batch.setProjectId(1L);
        batch.setTopicSource("manual");
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(101L);
        task.setTopic("topic");
        task.setTopicAsQuestion("question");
        task.setArticleType("buying_guide");
        task.setContentStyle("zhihu");
        task.setLength("medium");
        task.setArticleIndexInBatch(1);
        when(promptContextFactory.buildForBatch(batch, task))
                .thenThrow(new BizException(400, "Unregistered template variable: brandBasicInfo"));

        ReflectionTestUtils.invokeMethod(service, "runTask", batch, task);

        ArgumentCaptor<BatchArticleGenerationTask> captor = forClass(BatchArticleGenerationTask.class);
        verify(taskMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        BatchArticleGenerationTask failedTask = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("failed", failedTask.getStatus());
        assertThat(failedTask.getErrorMessage()).contains("Unregistered template variable");
    }

    @Test
    void completeBatchMarksPartialSuccessWhenSomeTasksFailed() {
        BatchArticleGenerationBatch batch = new BatchArticleGenerationBatch();
        batch.setId(10L);
        BatchArticleGenerationTask success = new BatchArticleGenerationTask();
        success.setStatus("success");
        BatchArticleGenerationTask failed = new BatchArticleGenerationTask();
        failed.setStatus("failed");
        when(taskMapper.selectList(any())).thenReturn(List.of(success, failed));
        when(batchMapper.selectById(10L)).thenReturn(batch);

        ReflectionTestUtils.invokeMethod(service, "completeBatch", 10L);

        ArgumentCaptor<BatchArticleGenerationBatch> captor = forClass(BatchArticleGenerationBatch.class);
        verify(batchMapper).updateById(captor.capture());
        BatchArticleGenerationBatch updated = captor.getValue();
        assertEquals("partial_success", updated.getStatus());
        assertEquals(1, updated.getSuccessCount());
        assertEquals(1, updated.getFailedCount());
    }

    @Test
    void validateAutoPlatformFailsFastWhenSpecialPerspectiveHasNoTemplate() {
        BatchArticleGenerateRequest.PlatformCount platform = platform("self_media", "wechat", 1);
        when(perspectiveService.resolve(2L, "self_media", "wechat"))
                .thenReturn(new TemplatePerspectiveService.ResolvedPerspective(
                        TemplatePerspectiveCodes.INDUSTRY_NEUTRAL,
                        TemplatePerspectiveService.MATCH_SCOPE_EXACT,
                        31L
                ));
        when(allocationService.allocate("self_media", "wechat", "problem_solution",
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL, 1)).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> ReflectionTestUtils.invokeMethod(
                service,
                "validateAutoPlatform",
                "0:0",
                2L,
                "topic",
                platform,
                "problem_solution",
                new java.util.ArrayList<>(),
                Map.of(),
                new java.util.HashMap<>()
        ));

        assertEquals(ContentErrorCodes.ARTICLE_BAD_REQUEST, ex.getCode());
        assertThat(ex.getMessage()).contains("特殊视角缺少启用模板")
                .contains("perspective=industry_neutral")
                .contains("questionScene=problem_solution");
    }

    @Test
    void validateAutoPlatformKeepsCustomerFallbackWhenNoTemplate() {
        BatchArticleGenerateRequest.PlatformCount platform = platform("self_media", "wechat", 2);
        when(perspectiveService.resolve(2L, "self_media", "wechat"))
                .thenReturn(TemplatePerspectiveService.ResolvedPerspective.customer());
        when(allocationService.allocate("self_media", "wechat", "problem_solution",
                TemplatePerspectiveCodes.CUSTOMER, 2)).thenReturn(List.of());
        List<BatchArticleGenerateResponse.Notice> notices = new java.util.ArrayList<>();

        List<?> result = ReflectionTestUtils.invokeMethod(
                service,
                "validateAutoPlatform",
                "0:0",
                2L,
                "topic",
                platform,
                "problem_solution",
                notices,
                Map.of(),
                new java.util.HashMap<>()
        );

        assertThat(result).isEmpty();
        assertThat(notices).hasSize(1);
        assertEquals("custom_template_skipped", notices.get(0).type());
        assertThat(notices.get(0).items()).hasSize(1);
        assertThat(notices.get(0).items().get(0).reason()).contains("未配置启用模板");
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
                null,
                "customer",
                TemplatePerspectiveService.MATCH_SCOPE_DEFAULT,
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

    private BatchArticleGenerateRequest.PlatformCount platform(String group, String sub, int count) {
        BatchArticleGenerateRequest.PlatformCount platform = new BatchArticleGenerateRequest.PlatformCount();
        platform.setChannelGroupCode(group);
        platform.setChannelSubCode(sub);
        platform.setAllocationMode("auto");
        platform.setCount(count);
        return platform;
    }

    private ArticleAutoImageInsertionService passThroughAutoImageInsertionService() {
        ArticleAutoImageInsertionService service = mock(ArticleAutoImageInsertionService.class);
        when(service.insertForChannel(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        return service;
    }
}
