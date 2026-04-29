package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.PackagePublishConfig;
import com.huanjing.geo.module.content.entity.ProjectPublishQuota;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.PackagePublishConfigMapper;
import com.huanjing.geo.module.content.mapper.ProjectPublishQuotaMapper;
import com.huanjing.geo.module.content.service.adapter.FailureKind;
import com.huanjing.geo.module.content.service.adapter.OfficialCmsSiteAdapter;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
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
    private PackagePublishConfigMapper packagePublishConfigMapper;
    private ProjectPublishQuotaMapper projectPublishQuotaMapper;
    private ProjectMapper projectMapper;
    private PublishSiteMapper publishSiteMapper;
    private CurrentUserService currentUserService;
    private TestOfficialCmsSiteAdapter officialCmsSiteAdapter;
    private ContentDistributionService contentDistributionService;
    private List<String> articleStatusUpdates;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DistributionTask.class);
        articleStatusUpdates = new ArrayList<>();
        articleDraftMapper = mock(ArticleDraftMapper.class);
        articleDraftVersionMapper = mock(ArticleDraftVersionMapper.class);
        distributionTaskMapper = mock(DistributionTaskMapper.class);
        packagePublishConfigMapper = mock(PackagePublishConfigMapper.class);
        projectPublishQuotaMapper = mock(ProjectPublishQuotaMapper.class);
        projectMapper = mock(ProjectMapper.class);
        publishSiteMapper = mock(PublishSiteMapper.class);
        currentUserService = mock(CurrentUserService.class);
        officialCmsSiteAdapter = new TestOfficialCmsSiteAdapter();
        contentDistributionService = new ContentDistributionService(
                articleDraftMapper,
                articleDraftVersionMapper,
                distributionTaskMapper,
                packagePublishConfigMapper,
                projectPublishQuotaMapper,
                projectMapper,
                publishSiteMapper,
                currentUserService,
                mock(SystemAlertService.class),
                List.of(officialCmsSiteAdapter),
                mock(BrandMapper.class)
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
}
