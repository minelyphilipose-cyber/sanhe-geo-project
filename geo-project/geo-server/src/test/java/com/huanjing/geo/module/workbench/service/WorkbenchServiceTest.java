package com.huanjing.geo.module.workbench.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

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
    private ProjectMapper projectMapper;
    private ReportMapper reportMapper;
    private ArticleDraftMapper articleDraftMapper;
    private DistributionTaskMapper distributionTaskMapper;
    private SystemAlertMapper systemAlertMapper;
    private SysUserMapper sysUserMapper;
    private SysPermissionMapper sysPermissionMapper;
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    private PublishSiteMapper publishSiteMapper;
    private WorkbenchService workbenchService;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        permissionService = mock(PermissionService.class);
        companyMapper = mock(CompanyMapper.class);
        brandMapper = mock(BrandMapper.class);
        projectMapper = mock(ProjectMapper.class);
        reportMapper = mock(ReportMapper.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
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
                projectMapper,
                reportMapper,
                articleDraftMapper,
                distributionTaskMapper,
                systemAlertMapper,
                sysUserMapper,
                sysPermissionMapper,
                aiPlatformConfigMapper,
                publishSiteMapper
        );
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

        workbenchService.operatorOverview();

        verify(currentUserService).ensurePermission("workbench.operator.read");

        ArgumentCaptor<LambdaQueryWrapper<Brand>> brandScope = wrapperCaptor();
        verify(brandMapper).selectCount(brandScope.capture());
        assertTrue(brandScope.getValue().getSqlSegment().contains("owner_id = 77"));

        ArgumentCaptor<LambdaQueryWrapper<Report>> reportScope = wrapperCaptor();
        verify(reportMapper).selectCount(reportScope.capture());
        assertTrue(reportScope.getValue().getSqlSegment().contains("owner_id = 77"));

        ArgumentCaptor<LambdaQueryWrapper<DistributionTask>> taskScope = wrapperCaptor();
        verify(distributionTaskMapper, org.mockito.Mockito.times(5)).selectCount(taskScope.capture());
        for (LambdaQueryWrapper<DistributionTask> wrapper : taskScope.getAllValues()) {
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("operator_id"));
            assertFalse(sql.contains("owner_id"));
        }
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
}
