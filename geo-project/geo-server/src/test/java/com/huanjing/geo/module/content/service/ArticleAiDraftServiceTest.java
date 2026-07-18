package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.*;
import com.huanjing.geo.common.llm.pool.LlmPermitScope;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.common.llm.router.LlmPlatformRouter;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.module.audit.*;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticleTypes;
import com.huanjing.geo.module.content.dto.*;
import com.huanjing.geo.module.content.entity.*;
import com.huanjing.geo.module.content.mapper.*;
import com.huanjing.geo.module.customer.access.*;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.*;
import com.huanjing.geo.module.system.mapper.*;
import com.huanjing.geo.module.system.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleAiDraftServiceTest {

    private ProjectMapper projectMapper;
    private BrandMapper brandMapper;
    private ArticleDraftMapper articleMapper;
    private ArticleDraftVersionMapper versionMapper;
    private AiPlatformConfigMapper configMapper;
    private CurrentUserService currentUserService;
    private BrandAccessService brandAccessService;
    private LlmInvoker llmInvoker;
    private LlmPlatformRouter llmPlatformRouter;
    private ArticleAiDraftRateLimiter rateLimiter;
    private AuditService auditService;
    private ArticleGenerationPromptContextFactory promptContextFactory;
    private BrandOfferingPromptSelector offeringPromptSelector;
    private ArticleAiDraftService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraft.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraftVersion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiPlatformConfig.class);

        projectMapper = mock(ProjectMapper.class);
        brandMapper = mock(BrandMapper.class);
        articleMapper = mock(ArticleDraftMapper.class);
        versionMapper = mock(ArticleDraftVersionMapper.class);
        configMapper = mock(AiPlatformConfigMapper.class);
        currentUserService = mock(CurrentUserService.class);
        brandAccessService = mock(BrandAccessService.class);
        PlatformCredentialService credentialService = mock(PlatformCredentialService.class);
        llmInvoker = mock(LlmInvoker.class);
        llmPlatformRouter = mock(LlmPlatformRouter.class);
        ArticleAiDraftPromptFilter promptFilter = mock(ArticleAiDraftPromptFilter.class);
        offeringPromptSelector = mock(BrandOfferingPromptSelector.class);
        rateLimiter = mock(ArticleAiDraftRateLimiter.class);
        auditService = mock(AuditService.class);
        SysDictItemMapper sysDictItemMapper = mock(SysDictItemMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(currentUserService.requireCurrentUser()).thenReturn(user(7L));
        when(projectMapper.selectById(10L)).thenReturn(project());
        when(brandMapper.selectById(20L)).thenReturn(brand());
        when(articleMapper.selectList(any())).thenReturn(List.of());
        when(configMapper.selectOne(any())).thenReturn(aiConfig());
        when(sysDictItemMapper.selectOne(any())).thenReturn(null);
        when(credentialService.resolveApiKey(eq("openai"), any(), any())).thenReturn("sk-test");
        when(promptFilter.filterOutboundPrompt(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
        when(promptFilter.filterOutboundPrompt(any(), any(), any(), anyBoolean())).thenAnswer(i -> i.getArgument(0));
        when(promptFilter.filterGeneratedContent(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
        when(promptFilter.filterGeneratedContent(any(), any(), any(), anyBoolean())).thenAnswer(i -> i.getArgument(0));
        when(offeringPromptSelector.select(any(), any(), any(), any(), any()))
                .thenReturn(new BrandOfferingPromptSelector.SelectionResult(
                        List.<BrandOfferingPromptSelector.SelectedOffering>of()));
        LlmCallFacade llmCallFacade = mock(LlmCallFacade.class);
        try {
            when(llmCallFacade.execute(any(LlmCallRequest.class))).thenAnswer(invocation -> {
                LlmCallRequest request = invocation.getArgument(0);
                if (request.routeRequest() != null) {
                    return LlmCallResult.routed(llmPlatformRouter.invoke(request.routeRequest()));
                }
                return LlmCallResult.direct(llmInvoker.invoke(request.prompt(), request.modelConfig()));
            });
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        ArticleModelResolver modelResolver = new ArticleModelResolver(configMapper, credentialService);
        ArticleGenerationEngine generationEngine = new ArticleGenerationEngine(
                llmCallFacade,
                modelResolver,
                mock(MarkdownImageReferenceValidator.class),
                promptFilter,
                mock(BatchArticleQualityChecker.class),
                mock(ArticleTitleDuplicateChecker.class)
        );
        promptContextFactory = mock(ArticleGenerationPromptContextFactory.class);
        BatchArticlePromptBuilder promptBuilder = new BatchArticlePromptBuilder(
                articleMapper,
                sysDictItemMapper,
                objectMapper,
                mock(ArticlePromptVariableRegistry.class)
        );

        service = new ArticleAiDraftService(projectMapper, brandMapper, articleMapper, versionMapper,
                currentUserService, brandAccessService, promptBuilder, promptContextFactory,
                offeringPromptSelector, generationEngine, mock(MedicalArticleComplianceChecker.class),
                mock(SpecialIndustryComplianceAlertService.class),
                mock(ArticleCoverSelectionService.class), passThroughAutoImageInsertionService(), rateLimiter, auditService,
                objectMapper, txManager(), Runnable::run);
    }

    private ArticleAutoImageInsertionService passThroughAutoImageInsertionService() {
        ArticleAutoImageInsertionService service = mock(ArticleAutoImageInsertionService.class);
        when(service.insertForChannel(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        when(service.insertForChannel(any(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(3));
        when(service.insertForTargetChannel(any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        return service;
    }

    @Test
    void generateCreatesApprovedAiDraft() throws Exception {
        mockInsertId();
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());

        ArticleAiDraftResponse response = service.generate(request()).get();

        assertEquals(99L, response.articleId());
        assertEquals("approved", response.status());
        verify(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.OPERATE);
        verify(rateLimiter).check(7L);

        ArgumentCaptor<ArticleDraft> draft = ArgumentCaptor.forClass(ArticleDraft.class);
        verify(articleMapper).insert(draft.capture());
        assertEquals("approved", draft.getValue().getStatus());

        ArgumentCaptor<ArticleDraftVersion> version = ArgumentCaptor.forClass(ArticleDraftVersion.class);
        verify(versionMapper).insert(version.capture());
        assertEquals("ai", version.getValue().getGeneratedBy());
        assertEquals("# AI title\n\nbody", version.getValue().getContentMarkdown());
        assertFalse(version.getValue().getPromptSnapshot().isBlank());
        verifyAudit(AuditResult.SUCCESS, "approved");
    }

    @Test
    void generateAddsOnlyNonBlankOfferingFieldsToPrompt() throws Exception {
        mockInsertId();
        when(offeringPromptSelector.select(any(), any(), any(), any(), any()))
                .thenReturn(new BrandOfferingPromptSelector.SelectionResult(List.of(
                        new BrandOfferingPromptSelector.SelectedOffering(
                                101L,
                                "舒缓芳疗",
                                List.of(),
                                null,
                                "久坐放松",
                                " ",
                                null
                        )
                )));
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        service.generate(request()).get();

        verify(llmInvoker).invoke(promptCaptor.capture(), any(LlmModelConfig.class));
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("- 舒缓芳疗"));
        assertTrue(prompt.contains("适用场景：久坐放松"));
        assertFalse(prompt.contains("目标人群："));
        assertFalse(prompt.contains("介绍："));
        assertFalse(prompt.contains("资质描述："));
    }

    @Test
    void previewReturnsMarkdownAndDoesNotPersistDraft() throws Exception {
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());

        ArticleAiDraftPreviewResponse response = service.preview(previewRequest()).get();

        assertEquals("AI title", response.title());
        assertEquals("# AI title\n\nbody", response.contentMarkdown());
        assertEquals("openai", response.modelPlatformCode());
        assertEquals("gpt-test", response.modelId());
        assertFalse(response.inputSnapshot().isBlank());
        assertTrue(response.modelResponseSnapshot().contains("responseText"));
        verify(articleMapper, never()).insert(any());
        verify(versionMapper, never()).insert(any());
        verifyAudit(AuditResult.SUCCESS, "preview_generated");
    }

    @Test
    void templateGeneratePersistsTemplateDraftWithPromptContext() throws Exception {
        mockInsertId();
        when(promptContextFactory.buildStrict(any(PromptContextRequest.class))).thenReturn(templateContext());
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());

        ArticleAiDraftResponse response = service.templateGenerate(templateRequest()).get();

        assertEquals(99L, response.articleId());
        assertEquals("approved", response.status());

        ArgumentCaptor<PromptContextRequest> requestCaptor = ArgumentCaptor.forClass(PromptContextRequest.class);
        verify(promptContextFactory).buildStrict(requestCaptor.capture());
        assertEquals("keyword_group", requestCaptor.getValue().topicSource());
        assertEquals(1, requestCaptor.getValue().articleIndexInBatch());
        assertEquals(31L, requestCaptor.getValue().keywordGroupId());

        ArgumentCaptor<ArticleDraft> draftCaptor = ArgumentCaptor.forClass(ArticleDraft.class);
        verify(articleMapper).insert(draftCaptor.capture());
        ArticleDraft draft = draftCaptor.getValue();
        assertEquals(10L, draft.getProjectId());
        assertEquals(ArticleTypes.INDUSTRY_ARTICLE, draft.getArticleType());
        assertEquals("wechat", draft.getContentStyle());
        assertEquals("social", draft.getChannelGroupCode());
        assertEquals("wechat_mp", draft.getChannelSubCode());
        assertEquals("recommendation", draft.getAgentSiteModule());
        assertEquals("industry_article", draft.getArticleTypeCode());
        assertEquals(41L, draft.getPromptTemplateId());
        assertEquals(42L, draft.getPromptTemplateVersionId());
        assertEquals("custom", draft.getAllocationMode());
        assertEquals("custom", draft.getTemplateSource());
        assertEquals("AI topic", draft.getTopic());
        assertEquals("AI topic?", draft.getTopicAsQuestion());
        assertEquals("AI title", draft.getTitle());

        ArgumentCaptor<ArticleDraftVersion> versionCaptor = ArgumentCaptor.forClass(ArticleDraftVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        ArticleDraftVersion version = versionCaptor.getValue();
        assertEquals(99L, version.getArticleId());
        assertEquals("# AI title\n\nbody", version.getContentMarkdown());
        assertEquals("openai", version.getModelPlatformCode());
        assertEquals("gpt-test", version.getModelId());
        assertEquals("template_ai", version.getGeneratedBy());
        assertEquals(7L, version.getCreatedBy());
        assertTrue(version.getPromptSnapshot().contains("\"contentSource\":\"AI_TEMPLATE\""));
        assertTrue(version.getInputSnapshot().contains("\"topic\":\"AI topic\""));
        verifyAudit(AuditResult.SUCCESS, "template_generation_generated");
    }

    @Test
    void templatePreviewUsesModelIdAsNameWhenRouterSelectionHasNoConfig() throws Exception {
        ArticleTemplatePreviewRequest request = templateRequest();
        request.setModelPlatformCode(null);
        request.setModelId(null);
        when(promptContextFactory.buildStrict(any(PromptContextRequest.class))).thenReturn(templateContext());
        LlmInvokeResult routedResult = llmResult();
        when(llmPlatformRouter.invoke(any())).thenReturn(new LlmRouteResult(
                "openai",
                "OpenAI",
                "primary",
                "gpt-test",
                "GPT Test",
                routedResult.responseText(),
                routedResult.durationMs(),
                1,
                routedResult
        ));

        ArticleTemplatePreviewResponse response = service.templatePreview(request).get();

        assertEquals("openai", response.modelPlatformCode());
        assertEquals("gpt-test", response.modelId());
        assertEquals("gpt-test", response.modelName());
        verify(llmInvoker, never()).invoke(any(), any(LlmModelConfig.class));
        verifyAudit(AuditResult.SUCCESS, "template_preview_generated");
    }

    @Test
    void previewUsesThreeMinuteModelTimeout() throws Exception {
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());
        ArgumentCaptor<LlmModelConfig> configCaptor = ArgumentCaptor.forClass(LlmModelConfig.class);

        service.preview(previewRequest()).get();

        verify(llmInvoker).invoke(any(), configCaptor.capture());
        assertEquals(180_000, configCaptor.getValue().requestTimeoutMs());
    }

    @Test
    void previewUsesIndustryObserverPromptAndContactGate() throws Exception {
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LlmModelConfig> configCaptor = ArgumentCaptor.forClass(LlmModelConfig.class);

        service.preview(previewRequest()).get();

        verify(llmInvoker).invoke(promptCaptor.capture(), configCaptor.capture());
        assertTrue(configCaptor.getValue().systemPrompt().contains("行业观察者"));
        assertTrue(promptCaptor.getValue().contains("AI topic"));
        assertTrue(promptCaptor.getValue().contains("# GEO（生成式引擎优化）可引用性要求"));
        assertTrue(promptCaptor.getValue().contains("# 平台风格规则"));
        assertFalse(promptCaptor.getValue().contains("{{contactBlock}}"));
        assertFalse(promptCaptor.getValue().contains("{{topic}}"));
        assertFalse(promptCaptor.getValue().contains("{{contentAngle}}"));
        assertFalse(promptCaptor.getValue().contains("对外公开电话"));
        assertFalse(promptCaptor.getValue().contains("对外公开地址"));
    }

    @Test
    void previewContactIntentUsesResolvedContactBlock() throws Exception {
        Brand brand = brand();
        brand.setPublicPhone("13812345678");
        brand.setPublicAddress("北京市朝阳区测试路88号");
        when(brandMapper.selectById(20L)).thenReturn(brand);
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        service.preview(contactPreviewRequest()).get();

        verify(llmInvoker).invoke(promptCaptor.capture(), any(LlmModelConfig.class));
        assertTrue(promptCaptor.getValue().contains("13812345678"));
        assertTrue(promptCaptor.getValue().contains("北京市朝阳区测试路88号"));
        assertFalse(promptCaptor.getValue().contains("{{contactBlock}}"));
    }

    @Test
    void previewRaisesConfiguredTimeoutBelowArticleMinimum() throws Exception {
        AiPlatformConfig config = aiConfig();
        config.setTimeoutMs(150_000);
        when(configMapper.selectOne(any())).thenReturn(config);
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());
        ArgumentCaptor<LlmModelConfig> configCaptor = ArgumentCaptor.forClass(LlmModelConfig.class);

        service.preview(previewRequest()).get();

        verify(llmInvoker).invoke(any(), configCaptor.capture());
        assertEquals(180_000, configCaptor.getValue().requestTimeoutMs());
    }

    @Test
    void previewUsesArticleFeatureAndConfiguredPlatformConcurrency() throws Exception {
        AiPlatformConfig config = aiConfig();
        config.setConcurrencyLimit(3);
        when(configMapper.selectOne(any())).thenReturn(config);
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());
        ArgumentCaptor<LlmModelConfig> configCaptor = ArgumentCaptor.forClass(LlmModelConfig.class);

        service.preview(previewRequest()).get();

        verify(llmInvoker).invoke(any(), configCaptor.capture());
        assertEquals("article", configCaptor.getValue().feature());
        assertEquals(3, configCaptor.getValue().concurrencyLimit());
    }

    @Test
    void llmFailureDoesNotPersistDraft() throws Exception {
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenThrow(new LlmInvokeException("boom"));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> service.generate(request()).get());

        assertEquals(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, ((BizException) ex.getCause()).getCode());
        assertEquals("AI 草稿生成失败：boom", ex.getCause().getMessage());
        verify(articleMapper, never()).insert(any());
        verify(versionMapper, never()).insert(any());
        verifyAudit(AuditResult.FAILURE, "generation_failed");
    }

    @Test
    void previewModelUnauthorizedReturnsConfigError() throws Exception {
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class)))
                .thenThrow(new LlmInvokeException("LLM invoke failed after retries: HTTP 401: unauthorized"));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> service.preview(previewRequest()).get());

        BizException cause = (BizException) ex.getCause();
        assertEquals(ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING, cause.getCode());
        assertEquals("AI 模型认证失败，请检查模型平台 API Key 配置", cause.getMessage());
        verify(articleMapper, never()).insert(any());
        verify(versionMapper, never()).insert(any());
        verifyAudit(AuditResult.FAILURE, "preview_failed");
    }

    @Test
    void previewPermitBusyReturnsUserFriendlyBusyMessage() throws Exception {
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class)))
                .thenThrow(new LlmPermitUnavailableException(LlmPermitScope.PLATFORM, "openai"));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> service.preview(previewRequest()).get());

        BizException cause = (BizException) ex.getCause();
        assertEquals(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, cause.getCode());
        assertEquals("AI 模型当前繁忙，请稍后重试", cause.getMessage());
        verify(articleMapper, never()).insert(any());
        verify(versionMapper, never()).insert(any());
        verifyAudit(AuditResult.FAILURE, "preview_failed");
    }

    @Test
    void brandAccessDeniedStopsBeforeRateLimitAndLlm() throws Exception {
        doThrow(new BizException(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, "denied"))
                .when(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.OPERATE);

        BizException ex = assertThrows(BizException.class, () -> service.generate(request()));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, ex.getCode());
        verify(rateLimiter, never()).check(any());
        verify(llmInvoker, never()).invoke(any(), any());
    }

    @Test
    void rateLimitStopsBeforeLlm() throws Exception {
        doThrow(new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_RATE_LIMITED, "limited"))
                .when(rateLimiter).check(7L);

        BizException ex = assertThrows(BizException.class, () -> service.generate(request()));

        assertEquals(ContentErrorCodes.ARTICLE_AI_DRAFT_RATE_LIMITED, ex.getCode());
        verify(llmInvoker, never()).invoke(any(), any());
    }

    @Test
    void aiDraftReviewWorkflowIsDisabled() throws Exception {
        mockInsertId();
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());
        Long articleId = service.generate(request()).get().articleId();
        when(articleMapper.selectById(articleId)).thenReturn(article("approved"));

        ArticleReviewRequest review = new ArticleReviewRequest();
        review.setAction("approve");
        BizException ex = assertThrows(BizException.class, () -> articleService().review(articleId, review));

        assertEquals(ContentErrorCodes.ARTICLE_BAD_REQUEST, ex.getCode());
        verify(articleMapper, never()).update(isNull(), any(Wrapper.class));
    }

    private ContentArticleService articleService() {
        return new ContentArticleService(articleMapper, versionMapper,
                mock(ArticleReviewLogMapper.class), mock(ArticlePublishLogMapper.class),
                mock(BatchArticleGenerationTaskMapper.class), mock(ArticlePromptTemplateMapper.class),
                mock(SelfMediaPublishScheduleMapper.class), mock(BrandMapper.class), projectMapper, mock(SysDictItemMapper.class), currentUserService,
                mock(MarkdownImageReferenceValidator.class), mock(com.huanjing.geo.module.content.service.render.wechat.WechatArticleRenderService.class),
                mock(ArticleImagePublicUrlRewriter.class), mock(ArticleAutoImageInsertionService.class),
                mock(ArticleCoverSelectionService.class),
                brandAccessService, mock(AuditService.class), mock(SpecialIndustryComplianceAlertService.class));
    }

    private void verifyAudit(AuditResult result, String status) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals("ARTICLE_AI_DRAFT_GENERATED", event.getEventType());
        assertEquals(result, event.getResult());
        assertEquals(status, event.getDetail().get("status"));
        assertFalse(event.getDetail().containsKey("prompt"));
    }

    private void mockInsertId() {
        doAnswer(i -> {
            ((ArticleDraft) i.getArgument(0)).setId(99L);
            return 1;
        }).when(articleMapper).insert(any(ArticleDraft.class));
    }

    private PlatformTransactionManager txManager() {
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        return tx;
    }

    private ArticleAiDraftRequest request() {
        ArticleAiDraftRequest req = new ArticleAiDraftRequest();
        req.setProjectId(10L);
        req.setArticleType(ArticleTypes.INDUSTRY_ARTICLE);
        req.setPrompt("write an article");
        req.setModelPlatformCode("openai");
        req.setModelId("gpt-test");
        return req;
    }

    private ArticleAiDraftPreviewRequest previewRequest() {
        ArticleAiDraftPreviewRequest req = new ArticleAiDraftPreviewRequest();
        req.setProjectId(10L);
        req.setArticleType(ArticleTypes.INDUSTRY_ARTICLE);
        req.setContentStyle("wechat");
        req.setTone("professional");
        req.setLength("medium");
        req.setTopic("AI topic");
        req.setExtraPrompt("extra");
        req.setReferenceMaterials("reference");
        req.setModelPlatformCode("openai");
        req.setModelId("gpt-test");
        return req;
    }

    private ArticleAiDraftPreviewRequest contactPreviewRequest() {
        ArticleAiDraftPreviewRequest req = previewRequest();
        req.setArticleType(ArticleTypes.FAQ);
        req.setTopic("怎么联系门店预约咨询");
        return req;
    }

    private ArticleTemplatePreviewRequest templateRequest() {
        ArticleTemplatePreviewRequest req = new ArticleTemplatePreviewRequest();
        req.setProjectId(10L);
        req.setArticleType(ArticleTypes.INDUSTRY_ARTICLE);
        req.setChannelGroupCode("social");
        req.setChannelSubCode("wechat_mp");
        req.setTopic("AI topic");
        req.setLength("medium");
        req.setKeywordGroupId(31L);
        req.setPromptTemplateId(41L);
        req.setPromptTemplateVersionId(42L);
        req.setModelPlatformCode("openai");
        req.setModelId("gpt-test");
        return req;
    }

    private ArticleGenerationPromptContextFactory.PromptContextResult templateContext() {
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(41L);
        template.setName("Template");
        template.setAgentSiteModule("recommendation");
        template.setArticleTypeCode("industry_article");

        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(42L);
        version.setTemplateId(41L);

        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project(),
                brand(),
                "Brand statement",
                "keyword_group",
                "AI topic",
                "AI topic?",
                31L,
                "Keyword group",
                List.of("AI", "GEO"),
                ArticleTypes.INDUSTRY_ARTICLE,
                "wechat",
                "medium",
                "extra",
                1,
                List.of("forbidden"),
                "title guide",
                "customer",
                TemplatePerspectiveService.MATCH_SCOPE_DEFAULT,
                null,
                List.<BrandOfferingPromptSelector.SelectedOffering>of()
        );
        BatchArticlePromptBuilder.PromptBuildResult prompt = new BatchArticlePromptBuilder.PromptBuildResult(
                "system prompt",
                "user prompt {{topic}}",
                "content angle",
                "audience",
                "{\"templateId\":41}",
                "{\"topic\":\"AI topic\"}"
        );
        return new ArticleGenerationPromptContextFactory.PromptContextResult(
                project(),
                brand(),
                input,
                prompt,
                List.of("forbidden"),
                template,
                version,
                "social",
                "wechat_mp",
                "wechat",
                "AI topic?",
                "customer",
                TemplatePerspectiveService.MATCH_SCOPE_DEFAULT,
                null,
                false
        );
    }

    private LlmInvokeResult llmResult() {
        return new LlmInvokeResult("# AI title\n\nbody", 10, 20, 123L, 0,
                LlmCallStatus.SUCCESS, "openai", "OpenAI", "gpt-test", "gpt-test");
    }

    private AiPlatformConfig aiConfig() {
        AiPlatformConfig config = new AiPlatformConfig();
        config.setPlatformCode("openai");
        config.setPlatformName("OpenAI");
        config.setEnabled(true);
        config.setEnabledForArticle(true);
        config.setApiUrl("https://api.example.com");
        config.setApiKey("encrypted");
        config.setModelId("gpt-test");
        config.setModelName("gpt-test");
        config.setConcurrencyLimit(2);
        return config;
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole("operator");
        user.setIsActive(true);
        return user;
    }

    private Project project() {
        Project project = new Project();
        project.setId(10L);
        project.setBrandId(20L);
        project.setPartnerId(30L);
        return project;
    }

    private Brand brand() {
        Brand brand = new Brand();
        brand.setId(20L);
        return brand;
    }

    private ArticleDraft article(String status) {
        ArticleDraft article = new ArticleDraft();
        article.setId(99L);
        article.setProjectId(10L);
        article.setStatus(status);
        return article;
    }

}
