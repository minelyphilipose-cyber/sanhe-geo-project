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
import com.huanjing.geo.module.content.entity.ProjectPublishQuota;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.PackagePublishConfigMapper;
import com.huanjing.geo.module.content.mapper.ProjectPublishQuotaMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.adapter.BrandGeoSiteAdapter;
import com.huanjing.geo.module.content.service.adapter.FailureKind;
import com.huanjing.geo.module.content.service.adapter.OfficialCmsSiteAdapter;
import com.huanjing.geo.module.content.service.adapter.ReviewStatusResult;
import com.huanjing.geo.module.content.service.adapter.SelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.content.service.adapter.ValidationResult;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.service.BrandService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private ProjectPublishQuotaMapper projectPublishQuotaMapper;
    private ProjectMapper projectMapper;
    private PublishSiteMapper publishSiteMapper;
    private CurrentUserService currentUserService;
    private TestOfficialCmsSiteAdapter officialCmsSiteAdapter;
    private TestBrandGeoSiteAdapter brandGeoSiteAdapter;
    private TestSelfMediaAdapter selfMediaAdapter;
    private BrandService brandService;
    private ProjectPublishQuotaService projectPublishQuotaService;
    private ContentDistributionService contentDistributionService;
    private List<String> articleStatusUpdates;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DistributionTask.class);
        articleStatusUpdates = new ArrayList<>();
        articleDraftMapper = mock(ArticleDraftMapper.class);
        articleDraftVersionMapper = mock(ArticleDraftVersionMapper.class);
        distributionTaskMapper = mock(DistributionTaskMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        packagePublishConfigMapper = mock(PackagePublishConfigMapper.class);
        projectPublishQuotaMapper = mock(ProjectPublishQuotaMapper.class);
        projectMapper = mock(ProjectMapper.class);
        publishSiteMapper = mock(PublishSiteMapper.class);
        currentUserService = mock(CurrentUserService.class);
        officialCmsSiteAdapter = new TestOfficialCmsSiteAdapter();
        brandGeoSiteAdapter = new TestBrandGeoSiteAdapter();
        selfMediaAdapter = new TestSelfMediaAdapter();
        brandService = mock(BrandService.class);
        projectPublishQuotaService = mock(ProjectPublishQuotaService.class);
        contentDistributionService = new ContentDistributionService(
                articleDraftMapper,
                articleDraftVersionMapper,
                distributionTaskMapper,
                selfMediaAccountMapper,
                packagePublishConfigMapper,
                projectPublishQuotaMapper,
                projectMapper,
                publishSiteMapper,
                currentUserService,
                mock(SystemAlertService.class),
                List.of(officialCmsSiteAdapter, brandGeoSiteAdapter),
                List.of(selfMediaAdapter),
                brandService,
                projectPublishQuotaService
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
        when(projectPublishQuotaMapper.tryReserve(20L, currentMonth(), 5)).thenReturn(1);
        officialCmsSiteAdapter.result = SubmitResult.success(201, "{}", "{\"id\":\"pa-1\"}", "https://site/article", "pa-1");
        when(distributionTaskMapper.selectById(300L)).thenReturn(task("submitted"));

        DistributionTask result = contentDistributionService.distributeTo(1L, target("active"));

        assertEquals("submitted", result.getStatus());
        verify(projectPublishQuotaMapper).tryReserve(20L, currentMonth(), 5);
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
        when(projectPublishQuotaMapper.tryReserve(20L, currentMonth(), 5)).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> contentDistributionService.distributeTo(1L, target("active")));

        assertEquals(400, ex.getCode());
        verify(distributionTaskMapper, never()).insert(any());
        verify(articleDraftMapper, never()).updateById(articleWithStatus("distributing"));
    }

    @Test
    void distributeTo_brandOfficialSite_adapter401_writesAuthExpired() {
        givenCommonData();
        when(projectPublishQuotaMapper.tryReserve(20L, currentMonth(), 5)).thenReturn(1);
        officialCmsSiteAdapter.result = SubmitResult.failure(401, "{}", "no", "HTTP 401", FailureKind.AUTH_EXPIRED, false);
        when(distributionTaskMapper.selectById(300L)).thenReturn(task("failed"));

        contentDistributionService.distributeTo(1L, target("active"));

        assertEquals(FailureKind.AUTH_EXPIRED, officialCmsSiteAdapter.result.getFailureKind());
        verify(projectPublishQuotaMapper).tryReserve(20L, currentMonth(), 5);
        verify(distributionTaskMapper).update(eq(null), any());
        assertArticleStatusTransitions("distributing", "approved");
    }

    @Test
    void distributeTo_brandOfficialSite_adapter5xx_writesServerErrorRetryable() {
        givenCommonData();
        when(projectPublishQuotaMapper.tryReserve(20L, currentMonth(), 5)).thenReturn(1);
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
        doThrow(new BizException(403, "No permission")).when(currentUserService).ensureBrandAccess(any(SysUser.class), eq(30L), eq("official_site"));

        BizException ex = assertThrows(BizException.class, () -> contentDistributionService.distributeTo(1L, target("active")));

        assertEquals(403, ex.getCode());
        verify(projectPublishQuotaMapper, never()).tryReserve(any(), any(), any());
        verify(distributionTaskMapper, never()).insert(any());
    }

    @Test
    void distributeTo_brandOfficialSite_inactiveSite_throws400() {
        givenCommonData();

        BizException ex = assertThrows(BizException.class, () -> contentDistributionService.distributeTo(1L, target("disabled")));

        assertEquals(400, ex.getCode());
        assertEquals("Brand official site is not active", ex.getMessage());
        verify(projectPublishQuotaMapper, never()).tryReserve(any(), any(), any());
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
        verify(projectPublishQuotaService).reserve(20L, currentMonth(), 5);
        verify(projectPublishQuotaService, never()).refund(any(), any());
        ArgumentCaptor<DistributionTask> inserted = ArgumentCaptor.forClass(DistributionTask.class);
        verify(distributionTaskMapper).insert(inserted.capture());
        assertEquals("submitting", inserted.getValue().getStatus());
        assertEquals(BrandGeoSiteAdapter.PLATFORM, inserted.getValue().getTargetKind());
        assertEquals(30L, inserted.getValue().getTargetBrandId());
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
        verify(projectPublishQuotaService).refund(20L, currentMonth());
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
        verify(projectPublishQuotaService).refund(20L, currentMonth());
        assertArticleStatusTransitions("distributing", "approved");
    }

    @Test
    void distributeTo_brandGeoSite_brandWithoutCode_throws400() {
        givenCommonData();
        givenBrandGeoSite(null, "active");

        BizException ex = assertThrows(BizException.class,
                () -> contentDistributionService.distributeTo(1L, new TargetContext.BrandGeoSiteTarget(30L, null)));

        assertEquals(400, ex.getCode());
        assertEquals("Brand has no GEO site configured", ex.getMessage());
        verify(projectPublishQuotaService, never()).reserve(any(), any(), any());
        verify(distributionTaskMapper, never()).insert(any());
    }

    @Test
    void distributeTo_brandGeoSite_disabled_throws400() {
        givenCommonData();
        givenBrandGeoSite("ok", "disabled");

        BizException ex = assertThrows(BizException.class,
                () -> contentDistributionService.distributeTo(1L, new TargetContext.BrandGeoSiteTarget(30L, "ok")));

        assertEquals(400, ex.getCode());
        assertEquals("Brand GEO site is not active", ex.getMessage());
        verify(projectPublishQuotaService, never()).reserve(any(), any(), any());
        verify(distributionTaskMapper, never()).insert(any());
    }

    @Test
    void distributeTo_brandGeoSite_quotaExhausted_throws400() {
        givenCommonData();
        givenBrandGeoSite("ok", "active");
        doThrow(new BizException(400, "Monthly publishing quota exhausted"))
                .when(projectPublishQuotaService).reserve(20L, currentMonth(), 5);

        BizException ex = assertThrows(BizException.class,
                () -> contentDistributionService.distributeTo(1L, new TargetContext.BrandGeoSiteTarget(30L, "ok")));

        assertEquals(400, ex.getCode());
        verify(distributionTaskMapper, never()).insert(any());
        verify(articleDraftMapper, never()).updateById(articleWithStatus("distributing"));
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
    void refreshDistributionTaskReviewStatus_publishedUpdatesReviewFields() {
        DistributionTask task = new DistributionTask();
        task.setId(700L);
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
        when(distributionTaskMapper.update(eq(null), any())).thenReturn(1);

        contentDistributionService.refreshDistributionTaskReviewStatus(700L);

        ArgumentCaptor<LambdaUpdateWrapper<DistributionTask>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(distributionTaskMapper).update(eq(null), updateCaptor.capture());
        String sqlSet = updateCaptor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("review_status"));
        assertTrue(sqlSet.contains("review_feedback"));
        assertTrue(sqlSet.contains("external_status"));
    }

    @Test
    void refreshDistributionTaskReviewStatus_unknownSkipsUpdate() {
        DistributionTask task = new DistributionTask();
        task.setId(701L);
        task.setTargetKind(DistributionTargetKind.MP_ACCOUNT);
        task.setSelfMediaAccountId(40L);
        SelfMediaAccount account = selfMediaAccount("douyin");
        selfMediaAdapter.platform = "douyin";
        selfMediaAdapter.reviewStatusResult = ReviewStatusResult.unknown(null, null, false, null);
        when(distributionTaskMapper.selectById(701L)).thenReturn(task);
        when(selfMediaAccountMapper.selectById(40L)).thenReturn(account);

        contentDistributionService.refreshDistributionTaskReviewStatus(701L);

        verify(distributionTaskMapper, never()).update(eq(null), any());
    }

    private void givenCommonData() {
        SysUser operator = new SysUser();
        operator.setId(100L);
        operator.setRole("operator");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        ArticleDraft article = articleWithStatus("approved");
        when(articleDraftMapper.selectById(1L)).thenReturn(article);
        when(articleDraftMapper.updateById(any(ArticleDraft.class))).thenAnswer(invocation -> {
            ArticleDraft updated = invocation.getArgument(0);
            articleStatusUpdates.add(updated.getStatus());
            return 1;
        });
        Project project = new Project();
        project.setId(20L);
        project.setPartnerId(200L);
        project.setBrandId(30L);
        project.setPackageType("basic");
        when(projectMapper.selectById(20L)).thenReturn(project);
        PackagePublishConfig config = new PackagePublishConfig();
        config.setMonthlyPublishLimit(5);
        when(packagePublishConfigMapper.selectOne(any())).thenReturn(config);
        ProjectPublishQuota quota = new ProjectPublishQuota();
        quota.setProjectId(20L);
        quota.setQuotaMonth(currentMonth());
        quota.setUsedCount(0);
        quota.setMonthlyLimit(5);
        when(projectPublishQuotaMapper.selectOne(any())).thenReturn(quota);
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
        brand.setGeoSiteCode(siteCode);
        brand.setGeoSiteStatus(status);
        when(brandService.requireBrandWithAccess(30L, true)).thenReturn(brand);
    }

    private ArticleDraft articleWithStatus(String status) {
        ArticleDraft article = new ArticleDraft();
        article.setId(1L);
        article.setProjectId(20L);
        article.setStatus(status);
        return article;
    }

    private void assertArticleStatusTransitions(String first, String second) {
        verify(articleDraftMapper, times(2)).updateById(any(ArticleDraft.class));
        assertEquals(List.of(first, second), articleStatusUpdates);
    }

    private DistributionTask task(String status) {
        DistributionTask task = new DistributionTask();
        task.setId(300L);
        task.setStatus(status);
        return task;
    }

    private String currentMonth() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM"));
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

        TestBrandGeoSiteAdapter() {
            super(null, null);
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
            return result;
        }
    }

    private static class TestSelfMediaAdapter implements SelfMediaAdapter {
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
