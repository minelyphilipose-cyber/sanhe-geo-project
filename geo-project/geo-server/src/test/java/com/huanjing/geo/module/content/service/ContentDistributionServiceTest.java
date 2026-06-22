package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.distribution.DistributionTargetKind;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.PackagePublishConfig;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.PackagePublishConfigMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.adapter.BrandGeoSiteAdapter;
import com.huanjing.geo.module.content.service.adapter.FailureKind;
import com.huanjing.geo.module.content.service.adapter.OfficialCmsSiteAdapter;
import com.huanjing.geo.module.content.service.adapter.ReviewStatusResult;
import com.huanjing.geo.module.content.service.adapter.AutoSelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.content.service.adapter.ValidationResult;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.authoritymedia.AuthorityMediaDistributionAdapter;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.service.BrandService;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.extension.service.FillTokenService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ContentDistributionServiceTest {

    private ArticleDraftMapper articleDraftMapper;
    private ArticleDraftVersionMapper articleDraftVersionMapper;
    private DistributionTaskMapper distributionTaskMapper;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private PackagePublishConfigMapper packagePublishConfigMapper;
    private ProjectMapper projectMapper;
    private PublishSiteMapper publishSiteMapper;
    private CurrentUserService currentUserService;
    private TestOfficialCmsSiteAdapter officialCmsSiteAdapter;
    private TestBrandGeoSiteAdapter brandGeoSiteAdapter;
    private TestSelfMediaAdapter selfMediaAdapter;
    private BrandService brandService;
    private CompanyPackageBindingService companyPackageBindingService;
    private CompanyChannelQuotaService companyChannelQuotaService;
    private BrandAccessService brandAccessService;
    private FillTokenService fillTokenService;
    private DistributionReviewStatusPollService reviewStatusPollService;
    private AuditService auditService;
    private ArticleImagePublicUrlRewriter articleImagePublicUrlRewriter;
    private ContentDistributionService contentDistributionService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DistributionTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraft.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        articleDraftVersionMapper = mock(ArticleDraftVersionMapper.class);
        distributionTaskMapper = mock(DistributionTaskMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        packagePublishConfigMapper = mock(PackagePublishConfigMapper.class);
        projectMapper = mock(ProjectMapper.class);
        publishSiteMapper = mock(PublishSiteMapper.class);
        currentUserService = mock(CurrentUserService.class);
        officialCmsSiteAdapter = new TestOfficialCmsSiteAdapter();
        brandGeoSiteAdapter = new TestBrandGeoSiteAdapter();
        selfMediaAdapter = new TestSelfMediaAdapter();
        brandService = mock(BrandService.class);
        companyPackageBindingService = mock(CompanyPackageBindingService.class);
        companyChannelQuotaService = mock(CompanyChannelQuotaService.class);
        brandAccessService = mock(BrandAccessService.class);
        fillTokenService = mock(FillTokenService.class);
        reviewStatusPollService = mock(DistributionReviewStatusPollService.class);
        auditService = mock(AuditService.class);
        articleImagePublicUrlRewriter = mock(ArticleImagePublicUrlRewriter.class);
        when(articleImagePublicUrlRewriter.rewrite(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        contentDistributionService = new ContentDistributionService(
                articleDraftMapper,
                articleDraftVersionMapper,
                distributionTaskMapper,
                selfMediaAccountMapper,
                packagePublishConfigMapper,
                projectMapper,
                publishSiteMapper,
                currentUserService,
                mock(SystemAlertService.class),
                List.of(officialCmsSiteAdapter, brandGeoSiteAdapter),
                List.of(selfMediaAdapter),
                List.of(),
                brandService,
                companyPackageBindingService,
                companyChannelQuotaService,
                brandAccessService,
                fillTokenService,
                reviewStatusPollService,
                mock(BrowserEnvironmentService.class),
                auditService,
                new ObjectMapper(),
                mock(AuthorityMediaDistributionAdapter.class),
                articleImagePublicUrlRewriter,
                mock(ArticleCoverSelectionService.class),
                mock(ForumBoardRoutingService.class)
        );
    }

    @Test
    void requireSite_frameworkRow_throws400() {
        Long frameworkSiteId = 99L;
        PublishSite mockSite = new PublishSite();
        mockSite.setId(frameworkSiteId);
        mockSite.setIsFramework(1);
        when(publishSiteMapper.selectById(frameworkSiteId)).thenReturn(mockSite);

        BizException ex = assertThrows(
                BizException.class,
                () -> ReflectionTestUtils.invokeMethod(contentDistributionService, "requireSite", frameworkSiteId)
        );

        assertEquals(400, ex.getCode());
        assertEquals("framework site is not a valid publish target", ex.getMessage());
    }

    @Test
    void distributeTo_brandOfficialSite_success_writesSubmittedAndQuotaIncreased() {
        givenCommonData();
        officialCmsSiteAdapter.result = SubmitResult.success(201, "{}", "{\"id\":\"pa-1\"}", "https://site/article", "pa-1");
        when(distributionTaskMapper.selectById(300L)).thenReturn(task("submitted"));

        DistributionTask result = contentDistributionService.distributeTo(1L, target("active"));

        assertEquals("submitted", result.getStatus());
        verify(companyChannelQuotaService).reserveDistribution(10L, 20L, DistributionTargetKind.BRAND_OFFICIAL_SITE, 300L);
        verify(companyChannelQuotaService).confirmDistribution(300L);
        ArgumentCaptor<DistributionTask> inserted = ArgumentCaptor.forClass(DistributionTask.class);
        verify(distributionTaskMapper).insert(inserted.capture());
        assertEquals("submitting", inserted.getValue().getStatus());
        assertEquals("brand_official_site", inserted.getValue().getTargetKind());
        assertEquals(10L, inserted.getValue().getBrandOfficialSiteId());
        assertNotNull(inserted.getValue().getLockedUntil());
        verify(distributionTaskMapper).update(eq(null), any());
        assertArticleStatusTransitions("distributing", "published");
    }

    @Test
    void distributeTo_brandOfficialSite_quotaExhausted_throws400() {
        givenCommonData();
        doThrow(new BizException(400, "Distribution quota exhausted for channel official_site"))
                .when(companyChannelQuotaService).reserveDistribution(10L, 20L, DistributionTargetKind.BRAND_OFFICIAL_SITE, 300L);

        BizException ex = assertThrows(BizException.class, () -> contentDistributionService.distributeTo(1L, target("active")));

        assertEquals(400, ex.getCode());
        verify(distributionTaskMapper).insert(any());
        verify(articleDraftMapper, never()).update(eq(null), any());
    }

    @Test
    void distributeTo_brandOfficialSite_adapter401_writesAuthExpired() {
        givenCommonData();
        officialCmsSiteAdapter.result = SubmitResult.failure(401, "{}", "no", "HTTP 401", FailureKind.AUTH_EXPIRED, false);
        when(distributionTaskMapper.selectById(300L)).thenReturn(task("failed"));

        contentDistributionService.distributeTo(1L, target("active"));

        assertEquals(FailureKind.AUTH_EXPIRED, officialCmsSiteAdapter.result.getFailureKind());
        verify(companyChannelQuotaService).reserveDistribution(10L, 20L, DistributionTargetKind.BRAND_OFFICIAL_SITE, 300L);
        verify(companyChannelQuotaService).refundDistribution(300L);
        verify(distributionTaskMapper).update(eq(null), any());
        assertArticleStatusTransitions("distributing", "approved");
    }

    @Test
    void confirmSemiAuto_marksPublishedAndConfirmsQuota() {
        givenCommonData();
        DistributionTask filled = semiAutoTask("filled");
        DistributionTask published = semiAutoTask("published");
        published.setPublishedUrl("https://mp.toutiao.com/article/1");
        when(distributionTaskMapper.selectById(300L)).thenReturn(filled, published);

        DistributionTask result = contentDistributionService.confirmSemiAuto(
                300L,
                "https://mp.toutiao.com/article/1",
                "{\"source\":\"admin_console\"}"
        );

        assertEquals("published", result.getStatus());
        verify(companyChannelQuotaService).confirmDistribution(300L);
        verify(distributionTaskMapper).update(eq(null), any());
        verify(articleDraftMapper).update(eq(null), any());
    }

    @Test
    void abandonSemiAuto_marksFailedAndRefundsQuota() {
        givenCommonData();
        DistributionTask filled = semiAutoTask("filled");
        DistributionTask failed = semiAutoTask("failed");
        when(distributionTaskMapper.selectById(300L)).thenReturn(filled, failed);
        when(distributionTaskMapper.abandonSemiAutoTask(eq(300L), eq("运营放弃"), any())).thenReturn(1);

        DistributionTask result = contentDistributionService.abandonSemiAuto(300L, "运营放弃");

        assertEquals("failed", result.getStatus());
        verify(companyChannelQuotaService).refundDistribution(300L);
        verify(distributionTaskMapper).abandonSemiAutoTask(eq(300L), eq("运营放弃"), any());
        verify(articleDraftMapper).update(eq(null), any());
    }

    @Test
    void distributeTo_brandOfficialSite_adapter5xx_writesServerErrorRetryable() {
        givenCommonData();
        officialCmsSiteAdapter.result = SubmitResult.failure(503, "{}", "down", "HTTP 503", FailureKind.SERVER_ERROR, true);
        when(distributionTaskMapper.selectById(300L)).thenReturn(task("failed"));

        contentDistributionService.distributeTo(1L, target("active"));

        assertEquals(FailureKind.SERVER_ERROR, officialCmsSiteAdapter.result.getFailureKind());
        assertTrue(officialCmsSiteAdapter.result.isRetryable());
        verify(distributionTaskMapper).update(eq(null), any());
        assertArticleStatusTransitions("distributing", "approved");
    }

    @Test
    void distributeTo_brandOfficialSite_brandAccessDenied_throws403() {
        givenCommonData();
        doThrow(new BizException(403, "No permission"))
                .when(brandAccessService).requireBrandAccess(eq(30L), eq(100L), eq(BrandAccessAction.OPERATE));

        BizException ex = assertThrows(BizException.class, () -> contentDistributionService.distributeTo(1L, target("active")));

        assertEquals(403, ex.getCode());
        verify(companyChannelQuotaService, never()).reserveDistribution(any(), any(), any(), any());
        verify(distributionTaskMapper, never()).insert(any());
    }

    @Test
    void distributeTo_brandOfficialSite_inactiveSite_throws400() {
        givenCommonData();

        BizException ex = assertThrows(BizException.class, () -> contentDistributionService.distributeTo(1L, target("disabled")));

        assertEquals(400, ex.getCode());
        assertEquals("Brand official site is not active", ex.getMessage());
        verify(companyChannelQuotaService, never()).reserveDistribution(any(), any(), any(), any());
        verify(distributionTaskMapper, never()).insert(any());
    }

    @Test
    void distributeTo_brandGeoSite_success_writesSubmittedAndRefundNotCalled() {
        givenCommonData();
        givenBrandGeoSite("ok", "active");
        brandGeoSiteAdapter.result = SubmitResult.success(200, "{\"siteCode\":\"ok\"}", "{\"code\":200}", "https://www.ok.com/knowledge/detail/12345", "12345");
        when(distributionTaskMapper.selectById(300L)).thenReturn(task("submitted"));

        DistributionTask result = contentDistributionService.distributeTo(1L, new TargetContext.BrandGeoSiteTarget(30L, "ignored"));

        assertEquals("submitted", result.getStatus());
        verify(companyChannelQuotaService).reserveDistribution(10L, 20L, DistributionTargetKind.BRAND_GEO_SITE, 300L);
        verify(companyChannelQuotaService).confirmDistribution(300L);
        verify(companyChannelQuotaService, never()).refundDistribution(any());
        ArgumentCaptor<DistributionTask> inserted = ArgumentCaptor.forClass(DistributionTask.class);
        verify(distributionTaskMapper).insert(inserted.capture());
        assertEquals("submitting", inserted.getValue().getStatus());
        assertEquals(BrandGeoSiteAdapter.PLATFORM, inserted.getValue().getTargetKind());
        assertEquals(30L, inserted.getValue().getTargetBrandId());
        assertEquals("ok Agent 官网", brandGeoSiteAdapter.capturedTarget.siteName());
        assertEquals("www.ok.com", brandGeoSiteAdapter.capturedTarget.domain());
        assertArticleStatusTransitions("distributing", "published");
    }

    @Test
    void distributeTo_brandGeoSite_failure_finalizesThenRefunds() {
        givenCommonData();
        givenBrandGeoSite("bad", "active");
        brandGeoSiteAdapter.result = SubmitResult.failure(400, "{\"siteCode\":\"bad\"}", "{\"code\":400}", "HTTP 400", FailureKind.CLIENT_ERROR, false);
        when(distributionTaskMapper.selectById(300L)).thenReturn(task("failed"));

        contentDistributionService.distributeTo(1L, new TargetContext.BrandGeoSiteTarget(30L, "ignored"));

        verify(distributionTaskMapper).update(eq(null), any());
        verify(companyChannelQuotaService).refundDistribution(300L);
        assertArticleStatusTransitions("distributing", "approved");
    }

    @Test
    void distributeTo_brandGeoSite_adapterException_writesUnknownFailureAndRefunds() {
        givenCommonData();
        givenBrandGeoSite("ok", "active");
        brandGeoSiteAdapter.throwUnexpected = true;
        when(distributionTaskMapper.selectById(300L)).thenReturn(task("failed"));

        contentDistributionService.distributeTo(1L, new TargetContext.BrandGeoSiteTarget(30L, "ignored"));

        verify(distributionTaskMapper).update(eq(null), any());
        verify(companyChannelQuotaService).refundDistribution(300L);
        assertArticleStatusTransitions("distributing", "approved");
    }

    @Test
    void distributeTo_brandGeoSite_brandWithoutCode_throws400() {
        givenCommonData();
        givenBrandGeoSite(null, "active");

        BizException ex = assertThrows(BizException.class,
                () -> contentDistributionService.distributeTo(1L, new TargetContext.BrandGeoSiteTarget(30L, null)));

        assertEquals(400, ex.getCode());
        assertEquals("Agent official site publish target is not configured", ex.getMessage());
        verify(companyChannelQuotaService, never()).reserveDistribution(any(), any(), any(), any());
        verify(distributionTaskMapper, never()).insert(any());
    }

    @Test
    void distributeTo_brandGeoSite_disabled_throws400() {
        givenCommonData();
        givenBrandGeoSite("ok", "disabled");

        BizException ex = assertThrows(BizException.class,
                () -> contentDistributionService.distributeTo(1L, new TargetContext.BrandGeoSiteTarget(30L, "ok")));

        assertEquals(400, ex.getCode());
        assertEquals("Agent official site publish target is not configured", ex.getMessage());
        verify(companyChannelQuotaService, never()).reserveDistribution(any(), any(), any(), any());
        verify(distributionTaskMapper, never()).insert(any());
    }

    @Test
    void distributeTo_brandGeoSite_quotaExhausted_throws400() {
        givenCommonData();
        givenBrandGeoSite("ok", "active");
        doThrow(new BizException(400, "Monthly publishing quota exhausted"))
                .when(companyChannelQuotaService).reserveDistribution(10L, 20L, DistributionTargetKind.BRAND_GEO_SITE, 300L);

        BizException ex = assertThrows(BizException.class,
                () -> contentDistributionService.distributeTo(1L, new TargetContext.BrandGeoSiteTarget(30L, "ok")));

        assertEquals(400, ex.getCode());
        verify(distributionTaskMapper).insert(any());
        verify(articleDraftMapper, never()).update(eq(null), any());
    }

    @Test
    void distributeTo_selfMedia_usesPlatformAdapterAndWritesSelfMediaTargetKind() {
        givenCommonData();
        selfMediaAdapter.result = SubmitResult.success(200, "{}", "{\"media_id\":\"draft-1\"}", null, "draft-1");
        selfMediaAdapter.result.setExternalStatus("accepted");
        selfMediaAdapter.result.setReviewStatus(ReviewStatusResult.ReviewStatus.UNDER_REVIEW);
        selfMediaAdapter.result.setReviewFeedback(null);
        when(distributionTaskMapper.selectById(300L)).thenReturn(task("submitted"));

        DistributionTask result = contentDistributionService.distributeTo(1L, selfMediaTarget("wechat_mp"));

        assertEquals("submitted", result.getStatus());
        ArgumentCaptor<DistributionTask> inserted = ArgumentCaptor.forClass(DistributionTask.class);
        verify(distributionTaskMapper).insert(inserted.capture());
        assertEquals(DistributionTargetKind.MP_ACCOUNT, inserted.getValue().getTargetKind());
        assertEquals("wechat_mp", inserted.getValue().getIntegrationMethod());
        assertEquals(40L, inserted.getValue().getSelfMediaAccountId());
        assertEquals("req-1", inserted.getValue().getRequestId());
        ArgumentCaptor<LambdaUpdateWrapper<DistributionTask>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(distributionTaskMapper).update(eq(null), updateCaptor.capture());
        String sqlSet = updateCaptor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("external_status"));
        assertTrue(sqlSet.contains("review_status"));
        assertTrue(sqlSet.contains("review_feedback"));
    }

    @Test
    void distributeTo_selfMedia_unregisteredPlatform_throws501() {
        givenCommonData();

        BizException ex = assertThrows(BizException.class,
                () -> contentDistributionService.distributeTo(1L, selfMediaTarget("douyin")));

        assertEquals(501, ex.getCode());
        assertEquals("Self-media platform not implemented: douyin", ex.getMessage());
        verify(companyChannelQuotaService, never()).reserveDistribution(any(), any(), any(), any());
        verify(distributionTaskMapper, never()).insert(any());
        verify(articleDraftMapper, never()).update(eq(null), any());
    }

    @Test
    void distributeTo_selfMedia_douyinPassesTargetFieldsToPlatformAdapter() {
        givenCommonData();
        selfMediaAdapter.platform = "douyin";
        selfMediaAdapter.result = SubmitResult.success(200, "{}", "{\"item_id\":\"item-1\"}", null, "item-1");
        when(distributionTaskMapper.selectById(300L)).thenReturn(task("submitted"));

        TargetContext.SelfMediaTarget target = selfMediaTarget(
                "douyin",
                null,
                List.of(101L, 102L),
                1,
                2,
                Map.of("text", "douyin text")
        );
        DistributionTask result = contentDistributionService.distributeTo(1L, target);

        assertEquals("submitted", result.getStatus());
        assertEquals("douyin", selfMediaAdapter.capturedTarget.account().getPlatform());
        assertEquals(List.of(101L, 102L), selfMediaAdapter.capturedTarget.imageMaterialIds());
        assertEquals(1, selfMediaAdapter.capturedTarget.privateStatus());
        assertEquals(2, selfMediaAdapter.capturedTarget.downloadType());
        assertEquals("douyin text", selfMediaAdapter.capturedTarget.platformOptions().get("text"));
    }

    @Test
    void distributeTo_selfMedia_orphanDistributingArticle_recoversAndCreatesNewTask() {
        givenCommonData();
        when(articleDraftMapper.selectById(1L)).thenReturn(articleWithStatus("distributing"));
        when(distributionTaskMapper.selectCount(any())).thenReturn(0L);
        selfMediaAdapter.result = SubmitResult.success(200, "{}", "{\"media_id\":\"draft-1\"}", null, "draft-1");
        when(distributionTaskMapper.selectById(300L)).thenReturn(task("submitted"));

        DistributionTask result = contentDistributionService.distributeTo(1L, selfMediaTarget("wechat_mp"));

        assertEquals("submitted", result.getStatus());
        verify(distributionTaskMapper).insert(any());
        ArgumentCaptor<LambdaUpdateWrapper<ArticleDraft>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(articleDraftMapper, times(3)).update(eq(null), updateCaptor.capture());
        List<Object> updatedValues = updateCaptor.getAllValues().stream()
                .flatMap(wrapper -> wrapper.getParamNameValuePairs().values().stream())
                .toList();
        assertTrue(updatedValues.contains("approved"));
        assertTrue(updatedValues.contains("distributing"));
        assertTrue(updatedValues.contains("distributed"));
    }

    @Test
    void distributeTo_selfMedia_distributingArticleWithActiveTask_stillBlocks() {
        givenCommonData();
        when(articleDraftMapper.selectById(1L)).thenReturn(articleWithStatus("distributing"));
        when(distributionTaskMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> contentDistributionService.distributeTo(1L, selfMediaTarget("wechat_mp")));

        assertEquals(400, ex.getCode());
        assertEquals("Article is already distributing and no reusable semi-auto task was found", ex.getMessage());
        verify(distributionTaskMapper, never()).insert(any());
    }

    @Test
    void refreshDistributionTaskReviewStatus_publishedUpdatesReviewFields() {
        givenCommonData();
        DistributionTask task = new DistributionTask();
        task.setId(700L);
        task.setArticleId(1L);
        task.setTargetKind(DistributionTargetKind.MP_ACCOUNT);
        task.setSelfMediaAccountId(40L);
        task.setResponsePayload("{\"_mock_review_outcome\":\"passed\"}");
        SelfMediaAccount account = selfMediaAccount("douyin");
        selfMediaAdapter.platform = "douyin";
        selfMediaAdapter.reviewStatusResult = new ReviewStatusResult(
                ReviewStatusResult.ReviewStatus.PUBLISHED,
                "passed",
                null,
                false,
                task.getResponsePayload()
        );
        when(distributionTaskMapper.selectById(700L)).thenReturn(task);
        when(selfMediaAccountMapper.selectById(40L)).thenReturn(account);
        DistributionTask refreshed = new DistributionTask();
        refreshed.setId(700L);
        refreshed.setReviewStatus("published");
        when(reviewStatusPollService.refreshTask(task)).thenReturn(refreshed);

        DistributionTask result = contentDistributionService.refreshDistributionTaskReviewStatus(700L);

        assertEquals("published", result.getReviewStatus());
        verify(reviewStatusPollService).refreshTask(task);
    }

    @Test
    void refreshDistributionTaskReviewStatus_unknownSkipsUpdate() {
        givenCommonData();
        DistributionTask task = new DistributionTask();
        task.setId(701L);
        task.setArticleId(1L);
        task.setTargetKind(DistributionTargetKind.MP_ACCOUNT);
        task.setSelfMediaAccountId(40L);
        SelfMediaAccount account = selfMediaAccount("douyin");
        selfMediaAdapter.platform = "douyin";
        selfMediaAdapter.reviewStatusResult = ReviewStatusResult.unknown(null, null, false, null);
        when(distributionTaskMapper.selectById(701L)).thenReturn(task);
        when(selfMediaAccountMapper.selectById(40L)).thenReturn(account);
        when(reviewStatusPollService.refreshTask(task)).thenReturn(task);

        contentDistributionService.refreshDistributionTaskReviewStatus(701L);

        verify(reviewStatusPollService).refreshTask(task);
    }

    @Test
    void distributionHistory_selfMediaTaskWithoutSiteIdReturnsAttempt() {
        givenCommonData();
        DistributionTask task = new DistributionTask();
        task.setId(800L);
        task.setArticleId(1L);
        task.setProjectId(20L);
        task.setTargetKind(DistributionTargetKind.MP_ACCOUNT);
        task.setSelfMediaAccountId(40L);
        task.setStatus("failed");
        task.setIntegrationMethod("toutiao");
        task.setAttemptNo(1);
        when(distributionTaskMapper.selectList(any())).thenReturn(List.of(task));

        Map<String, Object> result = contentDistributionService.distributionHistory(1L, null);

        assertEquals(1L, result.get("articleId"));
        @SuppressWarnings("unchecked")
        List<com.huanjing.geo.module.content.dto.DistributionAttemptVO> attempts =
                (List<com.huanjing.geo.module.content.dto.DistributionAttemptVO>) result.get("attempts");
        assertEquals(1, attempts.size());
        assertEquals(800L, attempts.get(0).getId());
        assertEquals(null, attempts.get(0).getSiteId());
        assertEquals(null, attempts.get(0).getSiteName());
        verify(publishSiteMapper, never()).selectList(any());
    }

    private void givenCommonData() {
        SysUser operator = new SysUser();
        operator.setId(100L);
        operator.setRole("operator");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        ArticleDraft article = articleWithStatus("approved");
        when(articleDraftMapper.selectById(1L)).thenReturn(article);
        when(articleDraftMapper.update(eq(null), any())).thenReturn(1);
        Project project = new Project();
        project.setId(20L);
        project.setCompanyId(10L);
        project.setPartnerId(200L);
        project.setBrandId(30L);
        project.setPackageType("basic");
        when(projectMapper.selectById(20L)).thenReturn(project);
        PackagePublishConfig config = new PackagePublishConfig();
        config.setMonthlyPublishLimit(5);
        when(packagePublishConfigMapper.selectOne(any())).thenReturn(config);
        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setArticleId(1L);
        version.setContentMarkdown("markdown");
        when(articleDraftVersionMapper.selectOne(any())).thenReturn(version);
        when(distributionTaskMapper.selectList(any())).thenReturn(List.of());
        when(distributionTaskMapper.insert(any(DistributionTask.class))).thenAnswer(invocation -> {
            DistributionTask task = invocation.getArgument(0);
            task.setId(300L);
            return 1;
        });
        when(distributionTaskMapper.update(eq(null), any())).thenReturn(1);
    }

    private TargetContext.BrandOfficialSiteTarget target(String status) {
        BrandOfficialSite site = new BrandOfficialSite();
        site.setId(10L);
        site.setBrandId(30L);
        site.setStatus(status);
        return new TargetContext.BrandOfficialSiteTarget(site);
    }

    private TargetContext.SelfMediaTarget selfMediaTarget(String platform) {
        return selfMediaTarget(platform, 50L, null, null, null, null);
    }

    private TargetContext.SelfMediaTarget selfMediaTarget(String platform,
                                                          Long coverMaterialId,
                                                          List<Long> imageMaterialIds,
                                                          Integer privateStatus,
                                                          Integer downloadType,
                                                          Map<String, Object> platformOptions) {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(40L);
        account.setBrandId(30L);
        account.setPlatform(platform);
        account.setStatus("active");
        return new TargetContext.SelfMediaTarget(account, coverMaterialId, imageMaterialIds, null, privateStatus, downloadType, "req-1", platformOptions);
    }

    private SelfMediaAccount selfMediaAccount(String platform) {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(40L);
        account.setBrandId(30L);
        account.setPlatform(platform);
        account.setStatus("active");
        return account;
    }

    private void givenBrandGeoSite(String siteCode, String status) {
        Brand brand = new Brand();
        brand.setId(30L);
        brand.setBrandName(siteCode == null ? "Brand" : siteCode);
        brand.setGeoSiteName(siteCode == null ? null : siteCode + " Agent 官网");
        brand.setGeoSiteDomain(siteCode == null ? null : "www." + siteCode + ".com");
        brand.setGeoSiteStatus(status);
        when(brandAccessService.requireBrandAccess(30L, 100L, BrandAccessAction.OPERATE)).thenReturn(brand);
    }

    private ArticleDraft articleWithStatus(String status) {
        ArticleDraft article = new ArticleDraft();
        article.setId(1L);
        article.setProjectId(20L);
        article.setStatus(status);
        return article;
    }

    private void assertArticleStatusTransitions(String first, String second) {
        ArgumentCaptor<LambdaUpdateWrapper<ArticleDraft>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(articleDraftMapper, times(2)).update(eq(null), updateCaptor.capture());
        List<LambdaUpdateWrapper<ArticleDraft>> updates = updateCaptor.getAllValues();
        assertStatusMutation(updates.get(0), first);
        assertStatusMutation(updates.get(1), second);
    }

    private void assertStatusMutation(LambdaUpdateWrapper<ArticleDraft> wrapper, String status) {
        assertTrue(wrapper.getParamNameValuePairs().values().contains(status));
    }

    private DistributionTask task(String status) {
        DistributionTask task = new DistributionTask();
        task.setId(300L);
        task.setStatus(status);
        return task;
    }

    private DistributionTask semiAutoTask(String status) {
        DistributionTask task = task(status);
        task.setArticleId(1L);
        task.setProjectId(20L);
        task.setTargetKind("mp_account");
        task.setDispatchMode("SEMI_AUTO");
        task.setSelfMediaAccountId(40L);
        return task;
    }

    private static class TestOfficialCmsSiteAdapter extends OfficialCmsSiteAdapter {
        private SubmitResult result;

        TestOfficialCmsSiteAdapter() {
            super(null, null, null, null);
        }

        @Override
        public boolean supportsPlatform(String platform) {
            return "official_cms".equals(platform);
        }

        @Override
        public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext target) {
            return result;
        }
    }

    private static class TestBrandGeoSiteAdapter extends BrandGeoSiteAdapter {
        private SubmitResult result;
        private boolean throwUnexpected;
        private TargetContext.BrandGeoSiteTarget capturedTarget;

        TestBrandGeoSiteAdapter() {
            super(null, null, new MarkdownToHtmlRenderer());
        }

        @Override
        public boolean supportsPlatform(String platform) {
            return BrandGeoSiteAdapter.PLATFORM.equals(platform);
        }

        @Override
        public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext target) {
            if (throwUnexpected) {
                throw new IllegalStateException("boom");
            }
            this.capturedTarget = (TargetContext.BrandGeoSiteTarget) target;
            return result;
        }

        @Override
        public EndpointProbeResult probeEndpoint(String domain) {
            return new EndpointProbeResult(true, "https://" + domain + "/api/v1/admin/content", 405, "ok");
        }
    }

    private static class TestSelfMediaAdapter implements AutoSelfMediaAdapter {
        private String platform = "wechat_mp";
        private SubmitResult result;
        private ReviewStatusResult reviewStatusResult = ReviewStatusResult.notApplicable();
        private TargetContext.SelfMediaTarget capturedTarget;

        @Override
        public String platform() {
            return platform;
        }

        @Override
        public ValidationResult validate(ArticleDraft article, String contentMarkdown, TargetContext.SelfMediaTarget target) {
            return ValidationResult.pass();
        }

        @Override
        public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext.SelfMediaTarget target) {
            this.capturedTarget = target;
            return result;
        }

        @Override
        public ReviewStatusResult refreshReviewStatus(DistributionTask task, SelfMediaAccount account) {
            return reviewStatusResult;
        }
    }
}
