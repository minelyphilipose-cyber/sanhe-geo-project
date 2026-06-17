package com.huanjing.geo.module.workbench.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.report.entity.Report;
import com.huanjing.geo.module.report.mapper.ReportMapper;
import com.huanjing.geo.module.system.entity.SysPermission;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.entity.SystemAlert;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.mapper.SysPermissionMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.mapper.SystemAlertMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkbenchServiceTest {

    private CurrentUserService currentUserService;
    private PermissionService permissionService;
    private CompanyMapper companyMapper;
    private BrandMapper brandMapper;
    private PresaleReportMapper presaleReportMapper;
    private ProjectMapper projectMapper;
    private ReportMapper reportMapper;
    private ArticleDraftMapper articleDraftMapper;
    private BatchArticleGenerationTaskMapper batchArticleGenerationTaskMapper;
    private DistributionTaskMapper distributionTaskMapper;
    private SystemAlertMapper systemAlertMapper;
    private SysUserMapper sysUserMapper;
    private SysPermissionMapper sysPermissionMapper;
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    private PublishSiteMapper publishSiteMapper;
    private WorkbenchService workbenchService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(Company.class);
        initTableInfo(Brand.class);
        initTableInfo(Project.class);
        initTableInfo(Report.class);
        initTableInfo(ArticleDraft.class);
        initTableInfo(DistributionTask.class);
        initTableInfo(PresaleReport.class);
        initTableInfo(SystemAlert.class);
    }

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        permissionService = mock(PermissionService.class);
        companyMapper = mock(CompanyMapper.class);
        brandMapper = mock(BrandMapper.class);
        presaleReportMapper = mock(PresaleReportMapper.class);
        projectMapper = mock(ProjectMapper.class);
        reportMapper = mock(ReportMapper.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        batchArticleGenerationTaskMapper = mock(BatchArticleGenerationTaskMapper.class);
        distributionTaskMapper = mock(DistributionTaskMapper.class);
        systemAlertMapper = mock(SystemAlertMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        sysPermissionMapper = mock(SysPermissionMapper.class);
        aiPlatformConfigMapper = mock(AiPlatformConfigMapper.class);
        publishSiteMapper = mock(PublishSiteMapper.class);
        workbenchService = new WorkbenchService(
                currentUserService,
                permissionService,
                companyMapper,
                brandMapper,
                presaleReportMapper,
                projectMapper,
                reportMapper,
                articleDraftMapper,
                batchArticleGenerationTaskMapper,
                distributionTaskMapper,
                systemAlertMapper,
                sysUserMapper,
                sysPermissionMapper,
                aiPlatformConfigMapper,
                publishSiteMapper
        );
    }

    @Test
    void salesOverviewUsesSalesOwnerForCustomersAndCreatedByForReports() {
        SysUser sales = user(66L, "sales");
        when(currentUserService.requireCurrentUser()).thenReturn(sales);
        when(companyMapper.selectCount(any())).thenReturn(3L);
        when(presaleReportMapper.selectCount(any())).thenReturn(7L);
        when(presaleReportMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 8, 0));

        var overview = workbenchService.salesOverview();

        verify(currentUserService).ensurePermission("workbench.sales.read");
        assertEquals(3L, overview.getCustomerCount());
        assertEquals(7L, overview.getReportCount());

        ArgumentCaptor<LambdaQueryWrapper<Company>> companyScope = wrapperCaptor();
        verify(companyMapper, org.mockito.Mockito.times(3)).selectCount(companyScope.capture());
        for (LambdaQueryWrapper<Company> wrapper : companyScope.getAllValues()) {
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("sales_owner_id"));
        }

        ArgumentCaptor<LambdaQueryWrapper<PresaleReport>> reportScope = wrapperCaptor();
        verify(presaleReportMapper, org.mockito.Mockito.times(5)).selectCount(reportScope.capture());
        for (LambdaQueryWrapper<PresaleReport> wrapper : reportScope.getAllValues()) {
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("created_by"));
            assertFalse(sql.contains("sales_owner_id"));
            assertFalse(sql.contains("owner_id"));
        }
    }

    @Test
    void salesOverviewBuildsTodosFromFailedReports() {
        SysUser sales = user(66L, "sales");
        when(currentUserService.requireCurrentUser()).thenReturn(sales);
        when(presaleReportMapper.selectCount(any())).thenReturn(1L);
        Page<PresaleReport> page = new Page<>(1, 8, 1);
        PresaleReport report = new PresaleReport();
        report.setId(99L);
        report.setBrandName("三和口腔");
        report.setStatus("FAILED");
        report.setUpdatedAt(LocalDateTime.of(2026, 6, 16, 11, 0));
        page.setRecords(List.of(report));
        when(presaleReportMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var overview = workbenchService.salesOverview();

        assertEquals(1L, overview.getOpenTodoCount());
        assertEquals(1L, overview.getHighSeverityTodoCount());
        assertEquals(1, overview.getPriorityTodos().size());
        assertEquals("presale_report", overview.getPriorityTodos().get(0).getSourceType());
        assertEquals("三和口腔", overview.getPriorityTodos().get(0).getBrandName());
        assertEquals("/admin/presale/report", overview.getPriorityTodos().get(0).getRoute());
    }

    @Test
    void operatorOverviewUsesOwnerScopeForAssetsAndOperatorIdForTasks() {
        SysUser operator = user(77L, "operator");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(companyMapper.selectCount(any())).thenReturn(1L);
        when(brandMapper.selectCount(any())).thenReturn(2L);
        when(projectMapper.selectCount(any())).thenReturn(3L);
        when(reportMapper.selectCount(any())).thenReturn(4L);
        when(articleDraftMapper.selectCount(any())).thenReturn(5L);
        when(distributionTaskMapper.selectCount(any())).thenReturn(6L);
        when(batchArticleGenerationTaskMapper.selectCount(any())).thenReturn(0L);

        workbenchService.operatorOverview();

        verify(currentUserService).ensurePermission("workbench.operator.read");

        ArgumentCaptor<LambdaQueryWrapper<Brand>> brandScope = wrapperCaptor();
        verify(brandMapper).selectCount(brandScope.capture());
        assertTrue(brandScope.getValue().getSqlSegment().contains("owner_id = 77"));

        ArgumentCaptor<LambdaQueryWrapper<Report>> reportScope = wrapperCaptor();
        verify(reportMapper).selectCount(reportScope.capture());
        assertTrue(reportScope.getValue().getSqlSegment().contains("owner_id = 77"));

        ArgumentCaptor<LambdaQueryWrapper<DistributionTask>> taskScope = wrapperCaptor();
        verify(distributionTaskMapper, org.mockito.Mockito.times(7)).selectCount(taskScope.capture());
        for (LambdaQueryWrapper<DistributionTask> wrapper : taskScope.getAllValues()) {
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("operator_id"));
            assertFalse(sql.contains("owner_id"));
        }
    }

    @Test
    void operatorOverviewMergesSystemAlertsAndDistributionTaskTodos() {
        SysUser operator = user(77L, "operator");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(distributionTaskMapper.selectCount(any()))
                .thenReturn(2L, 1L, 3L, 1L, 9L, 2L, 1L);
        when(systemAlertMapper.selectCount(any()))
                .thenReturn(1L, 1L);

        Page<SystemAlert> alertPage = new Page<>(1, 20, 1);
        alertPage.setRecords(List.of(alert(
                1L,
                "warn",
                "客户「三和医疗」品牌「三和口腔」的百家号账号授权还剩 6 天到期，请提前安排账号信息更新",
                "{\"companyName\":\"三和医疗\",\"brandName\":\"三和口腔\",\"route\":\"/admin/content/publish-platforms\"}",
                LocalDateTime.of(2026, 6, 16, 8, 0)
        )));
        when(systemAlertMapper.selectPage(any(Page.class), any())).thenReturn(alertPage);

        DistributionTask failed = distributionTask(
                10L,
                100L,
                200L,
                "failed",
                "AUTO",
                null,
                LocalDateTime.of(2026, 6, 16, 10, 0)
        );
        DistributionTask semiAuto = distributionTask(
                11L,
                101L,
                200L,
                "filled",
                "SEMI_AUTO",
                null,
                LocalDateTime.of(2026, 6, 16, 9, 0)
        );
        Page<DistributionTask> taskPage = new Page<>(1, 16, 2);
        taskPage.setRecords(List.of(semiAuto, failed));
        when(distributionTaskMapper.selectPage(any(Page.class), any())).thenReturn(taskPage);

        ArticleDraft failedArticle = article(100L, "失败文章");
        ArticleDraft semiAutoArticle = article(101L, "半自动文章");
        when(articleDraftMapper.selectBatchIds(any())).thenReturn(List.of(failedArticle, semiAutoArticle));

        Project project = new Project();
        project.setId(200L);
        project.setCompanyId(300L);
        project.setBrandId(400L);
        project.setCompanyName("项目客户名");
        project.setBrandName("项目品牌名");
        when(projectMapper.selectBatchIds(any())).thenReturn(List.of(project));

        Brand brand = new Brand();
        brand.setId(400L);
        brand.setCompanyId(300L);
        brand.setBrandName("三和口腔");
        when(brandMapper.selectBatchIds(any())).thenReturn(List.of(brand));

        Company company = new Company();
        company.setId(300L);
        company.setCompanyName("三和医疗");
        when(companyMapper.selectBatchIds(any())).thenReturn(List.of(company));

        var overview = workbenchService.operatorOverview();

        assertEquals(3L, overview.getOpenTodoCount());
        assertEquals(2L, overview.getHighSeverityTodoCount());
        assertEquals(3, overview.getPriorityTodos().size());
        assertEquals("distribution_task", overview.getPriorityTodos().get(0).getSourceType());
        assertEquals("失败文章", extractTitle(overview.getPriorityTodos().get(0).getMessage()));
        assertEquals("三和医疗", overview.getPriorityTodos().get(0).getCustomerName());
        assertEquals("三和口腔", overview.getPriorityTodos().get(0).getBrandName());
        assertEquals("/admin/content/execution", overview.getPriorityTodos().get(0).getRoute());
        assertEquals(1, overview.getCustomerRiskGroups().size());
        assertEquals(3L, overview.getCustomerRiskGroups().get(0).getRiskCount());
    }

    @Test
    void managerOverviewUsesSystemAlertVisibilityOnly() {
        SysUser manager = user(8L, "manager");
        when(currentUserService.requireCurrentUser()).thenReturn(manager);
        when(sysUserMapper.selectCount(any())).thenReturn(10L);
        when(sysPermissionMapper.selectCount(any())).thenReturn(11L);
        when(aiPlatformConfigMapper.selectCount(any())).thenReturn(12L);
        when(publishSiteMapper.selectCount(any())).thenReturn(13L);
        when(systemAlertMapper.selectCount(any())).thenReturn(14L);
        when(systemAlertMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 5, 0));

        workbenchService.managerOverview();

        verify(currentUserService).ensurePermission("workbench.manager.read");
        ArgumentCaptor<LambdaQueryWrapper<SystemAlert>> alertScope = wrapperCaptor();
        verify(systemAlertMapper, org.mockito.Mockito.times(2)).selectCount(alertScope.capture());
        for (LambdaQueryWrapper<SystemAlert> wrapper : alertScope.getAllValues()) {
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("recipient_user_id"));
            assertTrue(sql.contains("recipient_role"));
            assertFalse(sql.contains("dispatch"));
        }
    }

    @Test
    void managerOverviewBuildsTodosAndCustomerRiskGroupsFromSystemAlerts() {
        SysUser manager = user(8L, "manager");
        when(currentUserService.requireCurrentUser()).thenReturn(manager);
        when(systemAlertMapper.selectCount(any())).thenReturn(2L);
        Page<SystemAlert> emptyPage = new Page<>(1, 5, 0);
        Page<SystemAlert> todoPage = new Page<>(1, 20, 2);
        SystemAlert high = alert(
                1L,
                "high",
                "客户「三和医疗」品牌「三和口腔」的今日头条账号「测试头条」平台登录授权还剩 2 天到期，请优先更新账号信息",
                "{\"companyName\":\"三和医疗\",\"brandName\":\"三和口腔\",\"route\":\"/admin/content/publish-platforms\"}",
                LocalDateTime.of(2026, 6, 16, 9, 0)
        );
        SystemAlert warn = alert(
                2L,
                "warn",
                "客户「三和医疗」品牌「三和口腔」的抖音图文账号「测试抖音」官方 API 长期授权还剩 6 天到期，请提前安排账号信息更新",
                "{\"companyName\":\"三和医疗\",\"brandName\":\"三和口腔\",\"route\":\"/admin/content/publish-platforms\"}",
                LocalDateTime.of(2026, 6, 16, 8, 0)
        );
        todoPage.setRecords(List.of(warn, high));
        when(systemAlertMapper.selectPage(any(Page.class), any()))
                .thenReturn(emptyPage)
                .thenReturn(todoPage);

        var overview = workbenchService.managerOverview();

        assertEquals(2, overview.getPriorityTodos().size());
        assertEquals(1L, overview.getPriorityTodos().get(0).getId());
        assertEquals("三和医疗", overview.getPriorityTodos().get(0).getCustomerName());
        assertEquals("三和口腔", overview.getPriorityTodos().get(0).getBrandName());
        assertEquals(1, overview.getCustomerRiskGroups().size());
        assertEquals(2L, overview.getCustomerRiskGroups().get(0).getRiskCount());
        assertEquals(1L, overview.getCustomerRiskGroups().get(0).getHighSeverityCount());
    }

    @Test
    void superAdminOverviewRequiresWildcardPermission() {
        SysUser manager = user(8L, "manager");
        when(currentUserService.requireCurrentUser()).thenReturn(manager);
        when(permissionService.listPermKeys(manager)).thenReturn(Set.of("user.manage"));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.huanjing.geo.common.exception.BizException.class,
                () -> workbenchService.superAdminOverview()
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> ArgumentCaptor<LambdaQueryWrapper<T>> wrapperCaptor() {
        return ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    }

    private SysUser user(Long id, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole(role);
        user.setIsActive(true);
        return user;
    }

    private SystemAlert alert(Long id, String severity, String message, String contextJson, LocalDateTime createdAt) {
        SystemAlert alert = new SystemAlert();
        alert.setId(id);
        alert.setAlertType("SELF_MEDIA_ACCOUNT_AUTH_HEALTH");
        alert.setSeverity(severity);
        alert.setSource("self_media_account_health");
        alert.setMessage(message);
        alert.setContextJson(contextJson);
        alert.setCreatedAt(createdAt);
        return alert;
    }

    private DistributionTask distributionTask(Long id,
                                              Long articleId,
                                              Long projectId,
                                              String status,
                                              String dispatchMode,
                                              LocalDateTime nextRetryAt,
                                              LocalDateTime createdAt) {
        DistributionTask task = new DistributionTask();
        task.setId(id);
        task.setArticleId(articleId);
        task.setProjectId(projectId);
        task.setStatus(status);
        task.setDispatchMode(dispatchMode);
        task.setOperatorId(77L);
        task.setNextRetryAt(nextRetryAt);
        task.setCreatedAt(createdAt);
        return task;
    }

    private ArticleDraft article(Long id, String title) {
        ArticleDraft article = new ArticleDraft();
        article.setId(id);
        article.setTitle(title);
        return article;
    }

    private String extractTitle(String message) {
        int start = message.indexOf('《');
        int end = message.indexOf('》');
        if (start < 0 || end <= start) {
            return message;
        }
        return message.substring(start + 1, end);
    }

    private static void initTableInfo(Class<?> entityType) {
        try {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        } catch (IllegalStateException ignored) {
            // MyBatis-Plus keeps table metadata in a static cache shared across tests.
        }
    }
}
