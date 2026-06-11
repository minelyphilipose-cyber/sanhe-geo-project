package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticleTypes;
import com.huanjing.geo.module.content.dto.ArticlePublishRequest;
import com.huanjing.geo.module.content.dto.ArticleResubmitRequest;
import com.huanjing.geo.module.content.dto.ArticleReviewRequest;
import com.huanjing.geo.module.content.dto.ArticleRevisionSaveRequest;
import com.huanjing.geo.module.content.dto.ManualArticleCreateRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.ArticlePublishLogMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.ArticleReviewLogMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessErrorCodes;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.content.service.render.wechat.WechatArticleRenderService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentArticleServiceTest {

    private ArticleDraftMapper articleDraftMapper;
    private ArticleDraftVersionMapper articleDraftVersionMapper;
    private BatchArticleGenerationTaskMapper batchArticleGenerationTaskMapper;
    private ProjectMapper projectMapper;
    private BrandAccessService brandAccessService;
    private AuditService auditService;
    private ArticleImagePublicUrlRewriter articleImagePublicUrlRewriter;
    private ArticleAutoImageInsertionService autoImageInsertionService;
    private ArticleCoverSelectionService coverSelectionService;
    private ContentArticleService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraft.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraftVersion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BatchArticleGenerationTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Project.class);

        articleDraftMapper = mock(ArticleDraftMapper.class);
        articleDraftVersionMapper = mock(ArticleDraftVersionMapper.class);
        batchArticleGenerationTaskMapper = mock(BatchArticleGenerationTaskMapper.class);
        brandAccessService = mock(BrandAccessService.class);
        auditService = mock(AuditService.class);
        articleImagePublicUrlRewriter = mock(ArticleImagePublicUrlRewriter.class);
        autoImageInsertionService = mock(ArticleAutoImageInsertionService.class);
        coverSelectionService = mock(ArticleCoverSelectionService.class);
        projectMapper = mock(ProjectMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);

        when(currentUserService.requireCurrentUser()).thenReturn(operator(7L));
        when(projectMapper.selectById(10L)).thenReturn(project());
        when(articleImagePublicUrlRewriter.rewrite(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(autoImageInsertionService.insertForChannel(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        when(coverSelectionService.requireManualCoverUrl(any(), any())).thenReturn("https://example.test/cover.jpg");

        service = new ContentArticleService(
                articleDraftMapper,
                articleDraftVersionMapper,
                mock(ArticleReviewLogMapper.class),
                mock(ArticlePublishLogMapper.class),
                batchArticleGenerationTaskMapper,
                mock(ArticlePromptTemplateMapper.class),
                mock(BrandMapper.class),
                projectMapper,
                mock(SysDictItemMapper.class),
                currentUserService,
                mock(MarkdownImageReferenceValidator.class),
                mock(WechatArticleRenderService.class),
                articleImagePublicUrlRewriter,
                autoImageInsertionService,
                coverSelectionService,
                brandAccessService,
                auditService
        );
    }

    @Test
    void createManualPreservesMarkdownAndRequiresOperateAccess() {
        doAnswer(invocation -> {
            ArticleDraft draft = invocation.getArgument(0);
            draft.setId(99L);
            return 1;
        }).when(articleDraftMapper).insert(any(ArticleDraft.class));
        when(articleDraftMapper.selectList(any())).thenReturn(List.of());

        ManualArticleCreateRequest request = new ManualArticleCreateRequest();
        request.setProjectId(10L);
        request.setArticleType(ArticleTypes.INDUSTRY_ARTICLE);
        request.setContentStyle("zhihu");
        request.setCoverMaterialId(88L);
        request.setTopic("Manual topic");
        request.setTitle("Manual title");
        String markdown = "## Heading\n\n[official link](https://ok.example)\n\n- bullet";
        request.setContentMarkdown(markdown);

        service.createManual(request);

        verify(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.OPERATE);
        ArgumentCaptor<ArticleDraft> draftCaptor = ArgumentCaptor.forClass(ArticleDraft.class);
        verify(articleDraftMapper, times(1)).insert(draftCaptor.capture());
        assertEquals("zhihu", draftCaptor.getValue().getContentStyle());
        assertEquals(ArticleTypes.INDUSTRY_ARTICLE, draftCaptor.getValue().getArticleTypeCode());
        assertEquals("self_media", draftCaptor.getValue().getChannelGroupCode());
        assertEquals("zhihu", draftCaptor.getValue().getChannelSubCode());
        assertEquals("Manual topic", draftCaptor.getValue().getTopic());
        assertEquals("approved", draftCaptor.getValue().getStatus());
        ArgumentCaptor<ArticleDraftVersion> versionCaptor = ArgumentCaptor.forClass(ArticleDraftVersion.class);
        verify(articleDraftVersionMapper).insert(versionCaptor.capture());
        assertEquals(markdown, versionCaptor.getValue().getContentMarkdown());
        verifyAudit("ARTICLE_CREATED", AuditResult.SUCCESS);
    }

    @Test
    void createManualAllowsZhihuWithoutCover() {
        doAnswer(invocation -> {
            ArticleDraft draft = invocation.getArgument(0);
            draft.setId(99L);
            return 1;
        }).when(articleDraftMapper).insert(any(ArticleDraft.class));
        when(articleDraftMapper.selectList(any())).thenReturn(List.of());

        ManualArticleCreateRequest request = new ManualArticleCreateRequest();
        request.setProjectId(10L);
        request.setArticleType(ArticleTypes.INDUSTRY_ARTICLE);
        request.setContentStyle("zhihu");
        request.setTopic("Manual topic");
        request.setTitle("Manual title");
        request.setContentMarkdown("## Heading\n\nbody");

        service.createManual(request);

        ArgumentCaptor<ArticleDraft> draftCaptor = ArgumentCaptor.forClass(ArticleDraft.class);
        verify(articleDraftMapper, times(1)).insert(draftCaptor.capture());
        assertEquals("zhihu", draftCaptor.getValue().getContentStyle());
        assertNull(draftCaptor.getValue().getCoverImageUrl());
        verify(coverSelectionService, never()).requireManualCoverUrl(any(), any());
        verify(coverSelectionService, never()).selectRandomCoverUrl(any());
    }

    @Test
    void createManualStoresAiPreviewMetadataWhenProvided() {
        doAnswer(invocation -> {
            ArticleDraft draft = invocation.getArgument(0);
            draft.setId(99L);
            return 1;
        }).when(articleDraftMapper).insert(any(ArticleDraft.class));
        when(articleDraftMapper.selectList(any())).thenReturn(List.of());

        ManualArticleCreateRequest request = new ManualArticleCreateRequest();
        request.setProjectId(10L);
        request.setArticleType(ArticleTypes.INDUSTRY_ARTICLE);
        request.setContentStyle("toutiao");
        request.setTopic("AI topic");
        request.setTopicAsQuestion("AI question");
        request.setTitle("AI edited title");
        request.setContentMarkdown("# AI edited title\n\n## A\n\nbody");
        request.setSource("ai_preview");
        request.setAiMetadata(Map.of(
                "inputSnapshot", "{\"topic\":\"test\"}",
                "promptSnapshot", "{\"prompt\":\"test\"}",
                "modelResponseSnapshot", "{\"responseText\":\"raw\"}",
                "modelPlatformCode", "openai",
                "modelId", "gpt-test",
                "modelName", "GPT Test"
        ));
        when(coverSelectionService.selectRandomCoverUrl(20L)).thenReturn("https://example.test/random-cover.jpg");

        service.createManual(request);

        ArgumentCaptor<ArticleDraft> draftCaptor = ArgumentCaptor.forClass(ArticleDraft.class);
        verify(articleDraftMapper, times(1)).insert(draftCaptor.capture());
        assertEquals("toutiao", draftCaptor.getValue().getContentStyle());
        assertEquals("AI topic", draftCaptor.getValue().getTopic());
        assertEquals("AI question", draftCaptor.getValue().getTopicAsQuestion());
        assertEquals("https://example.test/random-cover.jpg", draftCaptor.getValue().getCoverImageUrl());
        assertEquals("approved", draftCaptor.getValue().getStatus());
        ArgumentCaptor<ArticleDraftVersion> versionCaptor = ArgumentCaptor.forClass(ArticleDraftVersion.class);
        verify(articleDraftVersionMapper).insert(versionCaptor.capture());
        ArticleDraftVersion version = versionCaptor.getValue();
        assertEquals("ai_preview", version.getGeneratedBy());
        assertEquals("{\"topic\":\"test\"}", version.getInputSnapshot());
        assertEquals("openai", version.getModelPlatformCode());
        assertEquals("gpt-test", version.getModelId());
        org.junit.jupiter.api.Assertions.assertTrue(version.getPromptSnapshot().contains("modelResponseSnapshot"));
    }

    @Test
    void createManualBrandAccessDeniedStopsBeforeInsert() {
        doThrow(new BizException(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, "denied"))
                .when(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.OPERATE);

        ManualArticleCreateRequest request = new ManualArticleCreateRequest();
        request.setProjectId(10L);
        request.setArticleType(ArticleTypes.INDUSTRY_ARTICLE);
        request.setContentStyle("wechat");
        request.setTopic("Manual topic");
        request.setTitle("Manual title");
        request.setContentMarkdown("content");

        BizException ex = assertThrows(BizException.class, () -> service.createManual(request));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, ex.getCode());
        verify(articleDraftMapper, never()).insert(any());
    }

    @Test
    void resubmitIsDisabled() {
        ArticleDraft article = article("under_revision");
        when(articleDraftMapper.selectById(99L)).thenReturn(article);

        ArticleResubmitRequest request = new ArticleResubmitRequest();
        request.setComment("ready");

        BizException ex = assertThrows(BizException.class, () -> service.resubmit(99L, request));

        assertEquals(ContentErrorCodes.ARTICLE_BAD_REQUEST, ex.getCode());
        verify(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.OPERATE);
        verify(articleDraftMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void saveRevisionKeepsArticleApproved() {
        ArticleDraft article = article("approved");
        when(articleDraftMapper.selectById(99L)).thenReturn(article);
        when(articleDraftMapper.selectList(any())).thenReturn(List.of());
        when(articleDraftMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        ArticleRevisionSaveRequest request = new ArticleRevisionSaveRequest();
        request.setTitle("Updated title");
        request.setContentMarkdown("# Updated title\n\nbody");
        request.setNote("edit");

        service.saveRevision(99L, request);

        verify(articleDraftVersionMapper).insert(any(ArticleDraftVersion.class));
        verify(articleDraftMapper).update(isNull(), any(Wrapper.class));
        verifyAudit("ARTICLE_REVISION_SAVED", AuditResult.SUCCESS, "approved");
    }

    @Test
    void reviewWorkflowIsDisabled() {
        ArticleDraft article = article("pending_review");
        when(articleDraftMapper.selectById(99L)).thenReturn(article);

        ArticleReviewRequest request = review("approve", null);
        BizException ex = assertThrows(BizException.class, () -> service.review(99L, request));

        assertEquals(ContentErrorCodes.ARTICLE_BAD_REQUEST, ex.getCode());
        verify(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.MANAGE);
        verify(articleDraftMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void reviewBrandAccessDeniedStopsBeforeUpdate() {
        ArticleDraft article = article("pending_review");
        when(articleDraftMapper.selectById(99L)).thenReturn(article);
        doThrow(new BizException(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, "denied"))
                .when(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.MANAGE);

        ArticleReviewRequest request = review("approve", null);
        BizException ex = assertThrows(BizException.class, () -> service.review(99L, request));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, ex.getCode());
        verify(articleDraftMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void publishBrandAccessDeniedStopsBeforeUpdate() {
        ArticleDraft article = article("approved");
        when(articleDraftMapper.selectById(99L)).thenReturn(article);
        doThrow(new BizException(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, "denied"))
                .when(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.OPERATE);

        ArticlePublishRequest request = new ArticlePublishRequest();
        request.setPublishAction("publish");
        BizException ex = assertThrows(BizException.class, () -> service.publish(99L, request));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, ex.getCode());
        verify(articleDraftMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void pageDoesNotBackfillLegacyContentStyleForListDisplay() {
        ArticleDraft article = article("approved");
        Page<ArticleDraft> mapperPage = new Page<>(1, 10, 1);
        mapperPage.setRecords(List.of(article));
        when(articleDraftMapper.selectPage(any(Page.class), any())).thenReturn(mapperPage);
        when(projectMapper.selectList(any())).thenReturn(List.of(project()));
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setArticleId(99L);
        task.setContentStyle("zhihu");
        task.setTopic("批量文章主题");
        task.setTopicAsQuestion("批量问题词");
        when(batchArticleGenerationTaskMapper.selectList(any())).thenReturn(List.of(task));

        Page<ArticleDraft> result = service.page(null, null, null, 1, 10);

        ArticleDraft row = result.getRecords().get(0);
        assertEquals("Project", row.getProjectName());
        assertNull(row.getContentStyle());
        assertEquals("批量文章主题", row.getTopic());
        assertEquals("批量问题词", row.getTopicAsQuestion());
        assertEquals(Boolean.TRUE, row.getSystemGenerated());
    }

    @Test
    void pageArticleTypeCodeFilterDoesNotFallbackToLegacyArticleType() {
        when(articleDraftMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10, 0));

        service.page(null, null, null, ArticleTypes.BUYING_GUIDE,
                null, null, null, null, null, 1, 10);

        ArgumentCaptor<Wrapper<ArticleDraft>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(articleDraftMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("article_type_code"));
        assertFalse(sql.matches("(?s).*\\barticle_type\\s*=.*"));
        assertFalse(sql.contains("IS NULL"));
    }

    @Test
    void pageChannelFilterDoesNotFallbackToLegacyContentStyle() {
        when(articleDraftMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10, 0));

        service.page(null, null, null, null,
                "self_media", "zhihu", null, null, null, 1, 10);

        ArgumentCaptor<Wrapper<ArticleDraft>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(articleDraftMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("channel_group_code"));
        assertTrue(sql.contains("channel_sub_code"));
        assertFalse(sql.contains("content_style"));
    }

    private void verifyAudit(String eventType, AuditResult result) {
        verifyAudit(eventType, result, null);
    }

    private void verifyAudit(String eventType, AuditResult result, String newStatus) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(eventType, event.getEventType());
        assertEquals(result, event.getResult());
        assertEquals(7L, event.getActorId());
        assertEquals(20L, event.getBrandId());
        if (newStatus != null) {
            assertEquals(newStatus, event.getDetail().get("newStatus"));
        }
    }

    private SysUser operator(Long id) {
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
        project.setProjectName("Project");
        return project;
    }

    private ArticleDraft article(String status) {
        ArticleDraft article = new ArticleDraft();
        article.setId(99L);
        article.setProjectId(10L);
        article.setArticleType(ArticleTypes.INDUSTRY_ARTICLE);
        article.setTitle("Article");
        article.setStatus(status);
        article.setCurrentVersionNo(1);
        article.setHasRisk(false);
        return article;
    }

    private ArticleReviewRequest review(String action, String comment) {
        ArticleReviewRequest request = new ArticleReviewRequest();
        request.setAction(action);
        request.setComment(comment);
        return request;
    }
}
