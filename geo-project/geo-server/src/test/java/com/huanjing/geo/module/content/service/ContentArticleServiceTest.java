package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticleTypes;
import com.huanjing.geo.module.content.dto.ArticlePublishRequest;
import com.huanjing.geo.module.content.dto.ArticleResubmitRequest;
import com.huanjing.geo.module.content.dto.ArticleReviewRequest;
import com.huanjing.geo.module.content.dto.ManualArticleCreateRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.ArticlePublishLogMapper;
import com.huanjing.geo.module.content.mapper.ArticleReviewLogMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessErrorCodes;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private BrandAccessService brandAccessService;
    private AuditService auditService;
    private ContentArticleService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraft.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraftVersion.class);

        articleDraftMapper = mock(ArticleDraftMapper.class);
        articleDraftVersionMapper = mock(ArticleDraftVersionMapper.class);
        brandAccessService = mock(BrandAccessService.class);
        auditService = mock(AuditService.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);

        when(currentUserService.requireCurrentUser()).thenReturn(operator(7L));
        when(projectMapper.selectById(10L)).thenReturn(project());

        service = new ContentArticleService(
                articleDraftMapper,
                articleDraftVersionMapper,
                mock(ArticleReviewLogMapper.class),
                mock(ArticlePublishLogMapper.class),
                mock(BrandMapper.class),
                projectMapper,
                mock(SysDictItemMapper.class),
                currentUserService,
                mock(MarkdownImageReferenceValidator.class),
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
        request.setTitle("Manual title");
        String markdown = "## Heading\n\n[official link](https://ok.example)\n\n- bullet";
        request.setContentMarkdown(markdown);

        service.createManual(request);

        verify(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.OPERATE);
        ArgumentCaptor<ArticleDraftVersion> versionCaptor = ArgumentCaptor.forClass(ArticleDraftVersion.class);
        verify(articleDraftVersionMapper).insert(versionCaptor.capture());
        assertEquals(markdown, versionCaptor.getValue().getContentMarkdown());
        verifyAudit("ARTICLE_CREATED", AuditResult.SUCCESS);
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

        service.createManual(request);

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
        request.setTitle("Manual title");
        request.setContentMarkdown("content");

        BizException ex = assertThrows(BizException.class, () -> service.createManual(request));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, ex.getCode());
        verify(articleDraftMapper, never()).insert(any());
    }

    @Test
    void resubmitUsesConditionalUpdateAndAuditsSuccess() {
        ArticleDraft article = article("under_revision");
        when(articleDraftMapper.selectById(99L)).thenReturn(article);
        when(articleDraftMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        ArticleResubmitRequest request = new ArticleResubmitRequest();
        request.setComment("ready");

        service.resubmit(99L, request);

        verify(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.OPERATE);
        verify(articleDraftMapper).update(isNull(), any(Wrapper.class));
        verifyAudit("ARTICLE_RESUBMITTED", AuditResult.SUCCESS);
    }

    @Test
    void reviewStateConflictWritesDeniedAudit() {
        ArticleDraft article = article("pending_review");
        when(articleDraftMapper.selectById(99L)).thenReturn(article);
        when(articleDraftVersionMapper.selectList(any())).thenReturn(List.of(version(8L)));
        when(articleDraftMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        ArticleReviewRequest request = review("approve", null);
        BizException ex = assertThrows(BizException.class, () -> service.review(99L, request));

        assertEquals(ContentErrorCodes.ARTICLE_STATE_CONFLICT, ex.getCode());
        verify(brandAccessService).requireBrandAccess(20L, 7L, BrandAccessAction.MANAGE);
        verifyAudit("ARTICLE_REVIEWED", AuditResult.DENIED);
    }

    @Test
    void reviewerCannotReviewOwnArticle() {
        ArticleDraft article = article("pending_review");
        when(articleDraftMapper.selectById(99L)).thenReturn(article);
        when(articleDraftVersionMapper.selectList(any())).thenReturn(List.of(version(8L), version(7L)));

        ArticleReviewRequest request = review("approve", null);
        BizException ex = assertThrows(BizException.class, () -> service.review(99L, request));

        assertEquals(ContentErrorCodes.ARTICLE_AUTHOR_CANNOT_REVIEW, ex.getCode());
        verify(articleDraftMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void reviewIgnoresNullHistoricalVersionAuthors() {
        ArticleDraft article = article("pending_review");
        when(articleDraftMapper.selectById(99L)).thenReturn(article);
        when(articleDraftVersionMapper.selectList(any())).thenReturn(Arrays.asList(null, version(null), version(8L)));
        when(articleDraftMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        service.review(99L, review("approve", null));

        verify(articleDraftMapper).update(isNull(), any(Wrapper.class));
        verifyAudit("ARTICLE_REVIEWED", AuditResult.SUCCESS);
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

    private void verifyAudit(String eventType, AuditResult result) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(eventType, event.getEventType());
        assertEquals(result, event.getResult());
        assertEquals(7L, event.getActorId());
        assertEquals(20L, event.getBrandId());
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

    private ArticleDraftVersion version(Long createdBy) {
        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setArticleId(99L);
        version.setVersionNo(1);
        version.setCreatedBy(createdBy);
        return version;
    }

    private ArticleReviewRequest review(String action, String comment) {
        ArticleReviewRequest request = new ArticleReviewRequest();
        request.setAction(action);
        request.setComment(comment);
        return request;
    }
}
