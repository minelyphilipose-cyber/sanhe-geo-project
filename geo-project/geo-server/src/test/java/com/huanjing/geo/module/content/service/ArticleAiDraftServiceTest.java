package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.*;
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
    private ArticleDraftMapper articleMapper;
    private ArticleDraftVersionMapper versionMapper;
    private AiPlatformConfigMapper configMapper;
    private CurrentUserService currentUserService;
    private BrandAccessService brandAccessService;
    private LlmInvoker llmInvoker;
    private ArticleAiDraftRateLimiter rateLimiter;
    private AuditService auditService;
    private ArticleAiDraftService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraft.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraftVersion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiPlatformConfig.class);

        projectMapper = mock(ProjectMapper.class);
        BrandMapper brandMapper = mock(BrandMapper.class);
        articleMapper = mock(ArticleDraftMapper.class);
        versionMapper = mock(ArticleDraftVersionMapper.class);
        configMapper = mock(AiPlatformConfigMapper.class);
        currentUserService = mock(CurrentUserService.class);
        brandAccessService = mock(BrandAccessService.class);
        PlatformCredentialService credentialService = mock(PlatformCredentialService.class);
        llmInvoker = mock(LlmInvoker.class);
        ArticleAiDraftPromptFilter promptFilter = mock(ArticleAiDraftPromptFilter.class);
        rateLimiter = mock(ArticleAiDraftRateLimiter.class);
        auditService = mock(AuditService.class);

        when(currentUserService.requireCurrentUser()).thenReturn(user(7L));
        when(projectMapper.selectById(10L)).thenReturn(project());
        when(brandMapper.selectById(20L)).thenReturn(brand());
        when(configMapper.selectOne(any())).thenReturn(aiConfig());
        when(credentialService.resolveApiKey(eq("openai"), any(), any())).thenReturn("sk-test");
        when(promptFilter.filterOutboundPrompt(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
        when(promptFilter.filterOutboundPrompt(any(), any(), any(), anyBoolean())).thenAnswer(i -> i.getArgument(0));
        when(promptFilter.filterGeneratedContent(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
        when(promptFilter.filterGeneratedContent(any(), any(), any(), anyBoolean())).thenAnswer(i -> i.getArgument(0));

        service = new ArticleAiDraftService(projectMapper, brandMapper, articleMapper, versionMapper, configMapper,
                currentUserService, brandAccessService, credentialService, llmInvoker,
                mock(MarkdownImageReferenceValidator.class), promptFilter, rateLimiter, auditService,
                new ObjectMapper(), txManager(), Runnable::run);
    }

    @Test
    void generateCreatesPendingReviewAiDraft() throws Exception {
        mockInsertId();
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());

        ArticleAiDraftResponse response = service.generate(request()).get();

        assertEquals(99L, response.articleId());
        assertEquals("pending_review", response.status());
        verify(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.OPERATE);
        verify(rateLimiter).check(7L);

        ArgumentCaptor<ArticleDraft> draft = ArgumentCaptor.forClass(ArticleDraft.class);
        verify(articleMapper).insert(draft.capture());
        assertEquals("pending_review", draft.getValue().getStatus());

        ArgumentCaptor<ArticleDraftVersion> version = ArgumentCaptor.forClass(ArticleDraftVersion.class);
        verify(versionMapper).insert(version.capture());
        assertEquals("ai", version.getValue().getGeneratedBy());
        assertEquals("# AI title\n\nbody", version.getValue().getContentMarkdown());
        assertFalse(version.getValue().getPromptSnapshot().isBlank());
        verifyAudit(AuditResult.SUCCESS, "pending_review");
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
    void previewUsesAtLeastOneHundredTwentySecondModelTimeout() throws Exception {
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());
        ArgumentCaptor<LlmModelConfig> configCaptor = ArgumentCaptor.forClass(LlmModelConfig.class);

        service.preview(previewRequest()).get();

        verify(llmInvoker).invoke(any(), configCaptor.capture());
        assertTrue(configCaptor.getValue().requestTimeoutMs() >= 120_000);
    }

    @Test
    void previewUsesIndustryObserverPromptAndContactGate() throws Exception {
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LlmModelConfig> configCaptor = ArgumentCaptor.forClass(LlmModelConfig.class);

        service.preview(previewRequest()).get();

        verify(llmInvoker).invoke(promptCaptor.capture(), configCaptor.capture());
        assertTrue(configCaptor.getValue().systemPrompt().contains("行业观察者"));
        assertTrue(promptCaptor.getValue().contains("如有必要可提及的品牌名"));
        assertTrue(promptCaptor.getValue().contains("# 品牌处理规则"));
        assertFalse(promptCaptor.getValue().contains("对外公开电话"));
        assertFalse(promptCaptor.getValue().contains("对外公开地址"));
    }

    @Test
    void previewHonorsConfiguredTimeoutAboveMinimum() throws Exception {
        AiPlatformConfig config = aiConfig();
        config.setTimeoutMs(150_000);
        when(configMapper.selectOne(any())).thenReturn(config);
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());
        ArgumentCaptor<LlmModelConfig> configCaptor = ArgumentCaptor.forClass(LlmModelConfig.class);

        service.preview(previewRequest()).get();

        verify(llmInvoker).invoke(any(), configCaptor.capture());
        assertEquals(150_000, configCaptor.getValue().requestTimeoutMs());
    }

    @Test
    void llmFailureDoesNotPersistDraft() throws Exception {
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenThrow(new LlmInvokeException("boom"));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> service.generate(request()).get());

        assertEquals(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, ((BizException) ex.getCause()).getCode());
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
    void aiDraftCannotBeApprovedByItsCreator() throws Exception {
        mockInsertId();
        when(llmInvoker.invoke(any(), any(LlmModelConfig.class))).thenReturn(llmResult());
        Long articleId = service.generate(request()).get().articleId();
        when(articleMapper.selectById(articleId)).thenReturn(article("pending_review"));
        when(versionMapper.selectList(any())).thenReturn(List.of(version(7L)));

        ArticleReviewRequest review = new ArticleReviewRequest();
        review.setAction("approve");
        BizException ex = assertThrows(BizException.class, () -> articleService().review(articleId, review));

        assertEquals(ContentErrorCodes.ARTICLE_AUTHOR_CANNOT_REVIEW, ex.getCode());
        verify(articleMapper, never()).update(isNull(), any(Wrapper.class));
    }

    private ContentArticleService articleService() {
        return new ContentArticleService(articleMapper, versionMapper,
                mock(ArticleReviewLogMapper.class), mock(ArticlePublishLogMapper.class), mock(BrandMapper.class),
                projectMapper, mock(SysDictItemMapper.class), currentUserService,
                mock(MarkdownImageReferenceValidator.class), brandAccessService, mock(AuditService.class));
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

    private ArticleDraftVersion version(Long createdBy) {
        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setArticleId(99L);
        version.setCreatedBy(createdBy);
        return version;
    }
}
