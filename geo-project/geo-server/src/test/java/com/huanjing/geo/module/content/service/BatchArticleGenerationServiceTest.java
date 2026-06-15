package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmCallStatus;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateResponse;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
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
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchArticleGenerationServiceTest {

    private ArticlePromptTemplateMapper promptTemplateMapper;
    private ArticlePromptTemplateVersionMapper promptTemplateVersionMapper;
    private ProjectMapper projectMapper;
    private BrandMapper brandMapper;
    private ArticleDraftMapper articleDraftMapper;
    private ArticleDraftVersionMapper articleDraftVersionMapper;
    private ArticleGenerationLogMapper articleGenerationLogMapper;
    private BatchArticleGenerationBatchMapper batchMapper;
    private BatchArticleGenerationTaskMapper taskMapper;
    private BatchArticlePromptBuilder promptBuilder;
    private ArticleGenerationPromptContextFactory promptContextFactory;
    private ArticleGenerationEngine articleGenerationEngine;
    private ArticleModelResolver articleModelResolver;
    private MedicalArticleGenerationService medicalArticleGenerationService;
    private MedicalArticleComplianceChecker medicalComplianceChecker;
    private BatchArticleQualityChecker qualityChecker;
    private ArticleTemplateAllocationService allocationService;
    private TemplatePerspectiveService perspectiveService;
    private ThirdPartySubjectRotationService subjectRotationService;
    private ArticleGenerationReadinessService readinessService;
    private BatchArticleGenerationService service;

    @BeforeEach
    void setUp() {
        promptTemplateMapper = mock(ArticlePromptTemplateMapper.class);
        promptTemplateVersionMapper = mock(ArticlePromptTemplateVersionMapper.class);
        projectMapper = mock(ProjectMapper.class);
        brandMapper = mock(BrandMapper.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        articleDraftVersionMapper = mock(ArticleDraftVersionMapper.class);
        articleGenerationLogMapper = mock(ArticleGenerationLogMapper.class);
        batchMapper = mock(BatchArticleGenerationBatchMapper.class);
        taskMapper = mock(BatchArticleGenerationTaskMapper.class);
        promptBuilder = mock(BatchArticlePromptBuilder.class);
        promptContextFactory = mock(ArticleGenerationPromptContextFactory.class);
        articleGenerationEngine = mock(ArticleGenerationEngine.class);
        articleModelResolver = mock(ArticleModelResolver.class);
        medicalArticleGenerationService = mock(MedicalArticleGenerationService.class);
        medicalComplianceChecker = mock(MedicalArticleComplianceChecker.class);
        qualityChecker = mock(BatchArticleQualityChecker.class);
        allocationService = mock(ArticleTemplateAllocationService.class);
        perspectiveService = mock(TemplatePerspectiveService.class);
        subjectRotationService = mock(ThirdPartySubjectRotationService.class);
        readinessService = mock(ArticleGenerationReadinessService.class);
        when(readinessService.detectTaskReadinessWarningCodes(any(), any(), any())).thenReturn(List.of());
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new BatchArticleGenerationService(
                projectMapper,
                brandMapper,
                mock(KeywordGroupMapper.class),
                mock(KeywordGroupResultMapper.class),
                mock(ProjectKeywordGroupRelMapper.class),
                articleDraftMapper,
                articleDraftVersionMapper,
                articleGenerationLogMapper,
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
                articleGenerationEngine,
                articleModelResolver,
                passThroughAutoImageInsertionService(),
                mock(ArticleCoverSelectionService.class),
                promptBuilder,
                promptContextFactory,
                medicalArticleGenerationService,
                medicalComplianceChecker,
                mock(SpecialIndustryComplianceAlertService.class),
                qualityChecker,
                allocationService,
                perspectiveService,
                new QuestionScenePlatformSuggestionService(),
                readinessService,
                subjectRotationService,
                new ObjectMapper(),
                transactionManager,
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
    void runTaskRetriesMedicalComplianceThreeTimesThenPersistsDiscardedArticle() throws Exception {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        project.setProjectName("Medical Project");
        project.setStatus("active");
        when(projectMapper.selectById(1L)).thenReturn(project);
        com.huanjing.geo.module.customer.entity.Brand brand = new com.huanjing.geo.module.customer.entity.Brand();
        brand.setId(2L);
        brand.setBrandName("星链口腔");
        when(brandMapper.selectById(2L)).thenReturn(brand);

        BatchArticleGenerationBatch batch = new BatchArticleGenerationBatch();
        batch.setId(10L);
        batch.setProjectId(1L);
        batch.setTopicSource("manual");
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(101L);
        task.setBatchId(10L);
        task.setProjectId(1L);
        task.setTopic("种植牙怎么选");
        task.setTopicAsQuestion("种植牙怎么选？");
        task.setArticleType("industry_article");
        task.setContentStyle("wechat");
        task.setChannelGroupCode("self_media");
        task.setChannelSubCode("wechat");
        task.setLength("medium");
        task.setArticleIndexInBatch(1);

        MedicalArticleGenerationService.MedicalPromptContext medicalContext = medicalContext();
        BatchArticlePromptBuilder.PromptBuildResult prompt = promptResult();
        when(promptContextFactory.buildForBatch(batch, task)).thenReturn(new ArticleGenerationPromptContextFactory.PromptContextResult(
                project,
                brand,
                null,
                prompt,
                List.of(),
                null,
                null,
                "self_media",
                "wechat",
                "wechat",
                "种植牙怎么选？",
                TemplatePerspectiveCodes.CUSTOMER,
                TemplatePerspectiveService.MATCH_SCOPE_DEFAULT,
                null,
                false,
                medicalContext
        ));
        when(articleModelResolver.resolve(null, null, "system", true))
                .thenReturn(new ArticleModelResolver.ModelSelection("mock", "mock-model", null));
        ArticleGenerationEngine.GeneratedArticle generated = generatedArticle();
        when(articleGenerationEngine.generate(any())).thenReturn(generated);
        MedicalArticleComplianceChecker.CheckResult failedResult = new MedicalArticleComplianceChecker.CheckResult(
                false,
                List.of(new MedicalArticleComplianceChecker.ComplianceIssue(7L, "absolute_claim", "block", "根治", "命中医疗合规规则"))
        );
        when(medicalComplianceChecker.check(any())).thenReturn(failedResult);
        when(medicalComplianceChecker.toJson(failedResult)).thenReturn("{\"passed\":false,\"issues\":[{\"ruleType\":\"absolute_claim\"}]}");
        when(articleDraftMapper.insert(any())).thenAnswer(invocation -> {
            ArticleDraft draft = invocation.getArgument(0);
            draft.setId(901L);
            return 1;
        });

        ReflectionTestUtils.invokeMethod(service, "runTask", batch, task);

        verify(articleGenerationEngine, times(MedicalArticleConstants.MAX_COMPLIANCE_GENERATION_ATTEMPTS)).generate(any());
        verify(medicalComplianceChecker, times(MedicalArticleConstants.MAX_COMPLIANCE_GENERATION_ATTEMPTS)).check(any());
        verify(medicalComplianceChecker, times(MedicalArticleConstants.MAX_COMPLIANCE_GENERATION_ATTEMPTS + 1))
                .logHits(any(), any(), any(), any());
        verify(medicalArticleGenerationService, never()).recordHistory(any(), any(), any(), any());
        verify(articleGenerationLogMapper, never()).insert(any());

        ArgumentCaptor<ArticleDraft> draftCaptor = forClass(ArticleDraft.class);
        verify(articleDraftMapper).insert(draftCaptor.capture());
        ArticleDraft discardedDraft = draftCaptor.getValue();
        assertEquals(MedicalArticleConstants.COMPLIANCE_DISCARDED, discardedDraft.getStatus());
        assertEquals(MedicalArticleConstants.COMPLIANCE_DISCARDED, discardedDraft.getComplianceStatus());
        assertEquals("oral", discardedDraft.getMedicalIndustryCode());
        assertEquals("implant", discardedDraft.getMedicalCategoryCode());

        ArgumentCaptor<ArticleDraftVersion> versionCaptor = forClass(ArticleDraftVersion.class);
        verify(articleDraftVersionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getInputSnapshot()).contains("medicalComplianceResult")
                .contains("absolute_claim");

        ArgumentCaptor<BatchArticleGenerationTask> taskCaptor = forClass(BatchArticleGenerationTask.class);
        verify(taskMapper, atLeast(1)).updateById(taskCaptor.capture());
        BatchArticleGenerationTask finalTask = taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1);
        assertEquals("failed", finalTask.getStatus());
        assertEquals(901L, finalTask.getDiscardedArticleId());
        assertEquals(MedicalArticleConstants.COMPLIANCE_DISCARDED, finalTask.getComplianceStatus());
        assertEquals(2, finalTask.getRetryCount());
        assertThat(finalTask.getComplianceIssuesJson()).contains("absolute_claim");
        assertThat(finalTask.getErrorMessage()).contains("医疗合规校验失败");
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
    void createSystemBatchFreezesThirdPartySubjectContextOnTask() {
        CapturingExecutor executor = new CapturingExecutor();
        ReflectionTestUtils.setField(service, "articleAiDraftExecutor", executor);

        Project project = new Project();
        project.setId(10L);
        project.setCompanyId(20L);
        project.setBrandId(30L);
        project.setStatus("active");
        project.setProjectName("Source project");
        Brand sourceBrand = new Brand();
        sourceBrand.setId(30L);
        sourceBrand.setBrandName("Source brand");
        when(projectMapper.selectById(10L)).thenReturn(project);
        when(brandMapper.selectById(30L)).thenReturn(sourceBrand);
        when(perspectiveService.resolve(30L, "self_media", "wechat"))
                .thenReturn(new TemplatePerspectiveService.ResolvedPerspective(
                        TemplatePerspectiveCodes.INDUSTRY_NEUTRAL,
                        TemplatePerspectiveService.MATCH_SCOPE_EXACT,
                        31L
                ));
        when(subjectRotationService.resolve(project, sourceBrand, "self_media", TemplatePerspectiveCodes.INDUSTRY_NEUTRAL))
                .thenReturn(new ThirdPartySubjectRotationService.RotationResult(30L, 10L, 300L, 3000L, true));

        ArticlePromptTemplate template = template(101L, "self_media", "wechat", "industry_article",
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL);
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(201L);
        version.setTemplateId(101L);
        when(allocationService.activeTemplates("self_media", "wechat", null,
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL))
                .thenReturn(List.of(new ArticleTemplateAllocationService.TemplateWithVersion(template, version)));
        when(allocationService.allocate("self_media", "wechat", null,
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL, 1))
                .thenReturn(List.of(new ArticleTemplateAllocationService.AllocatedTemplate(template, version, 1)));
        when(batchMapper.insert(any(BatchArticleGenerationBatch.class))).thenAnswer(invocation -> {
            BatchArticleGenerationBatch batch = invocation.getArgument(0);
            batch.setId(500L);
            return 1;
        });

        BatchArticleGenerateRequest req = new BatchArticleGenerateRequest();
        req.setProjectId(10L);
        req.setTopicSource("manual");
        BatchArticleGenerateRequest.TopicConfig topic = new BatchArticleGenerateRequest.TopicConfig();
        topic.setTopic("行业观察");
        topic.setPlatforms(List.of(platform("self_media", "wechat", 1)));
        req.setTopics(List.of(topic));

        BatchArticleGenerateResponse response = service.createSystemBatch(req, null);

        assertEquals(500L, response.batchId());
        assertThat(executor.commands).hasSize(1);
        ArgumentCaptor<BatchArticleGenerationTask> taskCaptor = forClass(BatchArticleGenerationTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        BatchArticleGenerationTask task = taskCaptor.getValue();
        assertEquals(500L, task.getBatchId());
        assertEquals(10L, task.getProjectId());
        assertEquals(30L, task.getSourceBrandId());
        assertEquals(300L, task.getSubjectBrandId());
        assertEquals(3000L, task.getSubjectProjectId());
        assertEquals(TemplatePerspectiveCodes.INDUSTRY_NEUTRAL, task.getPerspectiveCode());
        assertEquals(31L, task.getPerspectiveMatchedConfigId());
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

    @Test
    void submitBatchTasksDispatchesEachTaskToArticleExecutor() {
        CapturingExecutor executor = new CapturingExecutor();
        ReflectionTestUtils.setField(service, "articleAiDraftExecutor", executor);
        BatchArticleGenerationBatch batch = new BatchArticleGenerationBatch();
        batch.setId(77L);
        BatchArticleGenerationTask first = generationTask(101L, 77L);
        BatchArticleGenerationTask second = generationTask(102L, 77L);

        ReflectionTestUtils.invokeMethod(service, "submitBatchTasks", batch, List.of(first, second));

        assertThat(executor.commands).hasSize(2);
    }

    @Test
    void recoverStalledBatchesResubmitsPendingTasks() {
        CapturingExecutor executor = new CapturingExecutor();
        ReflectionTestUtils.setField(service, "articleAiDraftExecutor", executor);
        BatchArticleGenerationBatch batch = new BatchArticleGenerationBatch();
        batch.setId(77L);
        batch.setStatus("pending");
        batch.setUpdatedAt(LocalDateTime.now().minusMinutes(30));
        BatchArticleGenerationTask task = generationTask(101L, 77L);
        task.setStatus("pending");
        when(batchMapper.selectList(any())).thenReturn(List.of(batch));
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        int recovered = service.recoverStalledBatches(10, Duration.ofMinutes(15));

        assertEquals(1, recovered);
        assertThat(executor.commands).hasSize(1);
        verify(taskMapper, never()).updateById(task);
    }

    @Test
    void recoverStalledBatchesResetsStaleRunningTasks() {
        CapturingExecutor executor = new CapturingExecutor();
        ReflectionTestUtils.setField(service, "articleAiDraftExecutor", executor);
        BatchArticleGenerationBatch batch = new BatchArticleGenerationBatch();
        batch.setId(77L);
        batch.setStatus("running");
        batch.setUpdatedAt(LocalDateTime.now().minusMinutes(30));
        BatchArticleGenerationTask task = generationTask(101L, 77L);
        task.setStatus("running");
        task.setUpdatedAt(LocalDateTime.now().minusMinutes(30));
        task.setStartedAt(LocalDateTime.now().minusMinutes(30));
        when(batchMapper.selectList(any())).thenReturn(List.of(batch));
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        int recovered = service.recoverStalledBatches(10, Duration.ofMinutes(15));

        assertEquals(1, recovered);
        assertEquals("pending", task.getStatus());
        assertThat(executor.commands).hasSize(1);
        verify(taskMapper).updateById(task);
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
                null,
                List.<BrandOfferingPromptSelector.SelectedOffering>of()
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

    private MedicalArticleGenerationService.MedicalPromptContext medicalContext() {
        return new MedicalArticleGenerationService.MedicalPromptContext(
                "oral",
                "education",
                "implant",
                "种植牙",
                11L,
                "种植牙术前评估通常关注哪些条件",
                "medical_decision",
                "risk",
                "kernel",
                2,
                false,
                "style",
                false,
                "qualification",
                "license",
                "scope",
                null
        );
    }

    private ArticleGenerationEngine.GeneratedArticle generatedArticle() {
        LlmInvokeResult result = new LlmInvokeResult(
                "# 根治种植牙问题\n内容宣称根治，但没有合规表达。",
                10,
                20,
                100L,
                0,
                LlmCallStatus.SUCCESS,
                "mock",
                "Mock",
                "mock-model",
                "Mock Model"
        );
        return new ArticleGenerationEngine.GeneratedArticle(
                "根治种植牙问题",
                "# 根治种植牙问题\n内容宣称根治，但没有合规表达。",
                new ArticleModelResolver.ModelSelection("mock", "mock-model", null),
                result,
                new BatchArticleQualityChecker.QualityResult("passed", false, List.of())
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

    private ArticlePromptTemplate template(Long id,
                                           String channelGroupCode,
                                           String channelSubCode,
                                           String articleTypeCode,
                                           String perspectiveCode) {
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(id);
        template.setName("template-" + id);
        template.setChannelGroupCode(channelGroupCode);
        template.setChannelSubCode(channelSubCode);
        template.setArticleTypeCode(articleTypeCode);
        template.setQuestionSceneCode("problem_solution");
        template.setPerspectiveCode(perspectiveCode);
        template.setWeight(10);
        template.setContactDisclosureMode("brand_only");
        return template;
    }

    private ArticleAutoImageInsertionService passThroughAutoImageInsertionService() {
        ArticleAutoImageInsertionService service = mock(ArticleAutoImageInsertionService.class);
        when(service.insertForChannel(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        return service;
    }

    private BatchArticleGenerationTask generationTask(Long id, Long batchId) {
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(id);
        task.setBatchId(batchId);
        return task;
    }

    private static class CapturingExecutor implements Executor {
        private final List<Runnable> commands = new java.util.ArrayList<>();

        @Override
        public void execute(Runnable command) {
            commands.add(command);
        }
    }
}
