package com.huanjing.geo.module.workbench.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
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
import com.huanjing.geo.module.system.dto.SystemAlertTodoVO;
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
import com.huanjing.geo.module.workbench.dto.ManagerWorkbenchOverviewVO;
import com.huanjing.geo.module.workbench.dto.OperatorWorkbenchOverviewVO;
import com.huanjing.geo.module.workbench.dto.SuperAdminWorkbenchOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkbenchService {

    private static final Set<String> ACTIVE_PROJECT_STATUSES = Set.of("active", "pending_start", "paused");
    private static final Set<String> COMPLETED_DISTRIBUTION_STATUSES = Set.of("submitted", "confirmed", "published");
    private static final Set<String> IN_FLIGHT_EXTENSION_STATUSES = Set.of("token_issued", "filling", "filled");

    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final CompanyMapper companyMapper;
    private final BrandMapper brandMapper;
    private final ProjectMapper projectMapper;
    private final ReportMapper reportMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final SystemAlertMapper systemAlertMapper;
    private final SysUserMapper sysUserMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PublishSiteMapper publishSiteMapper;

    public OperatorWorkbenchOverviewVO operatorOverview() {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("workbench.operator.read");
        Long operatorId = user.getId();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        OperatorWorkbenchOverviewVO vo = new OperatorWorkbenchOverviewVO();
        vo.setCustomerCount(companyMapper.selectCount(new LambdaQueryWrapper<Company>()
                .isNull(Company::getDeletedAt)
                .eq(Company::getOwnerId, operatorId)));
        vo.setBrandCount(brandMapper.selectCount(new LambdaQueryWrapper<Brand>()
                .isNull(Brand::getDeletedAt)
                .inSql(Brand::getCompanyId, ownerCompanyIdSql(operatorId))));
        vo.setProjectCount(projectMapper.selectCount(ownerProjectWrapper(operatorId)));
        vo.setActiveProjectCount(projectMapper.selectCount(ownerProjectWrapper(operatorId)
                .in(Project::getStatus, ACTIVE_PROJECT_STATUSES)));
        vo.setHighRiskProjectCount(projectMapper.selectCount(ownerProjectWrapper(operatorId)
                .eq(Project::getStage, "high_risk")));
        vo.setMonthlyReportCount(reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .inSql(Report::getProjectId, ownerProjectIdSql(operatorId))
                .ge(Report::getCreatedAt, monthStart)));
        vo.setMonthlyArticleCount(articleDraftMapper.selectCount(new LambdaQueryWrapper<ArticleDraft>()
                .inSql(ArticleDraft::getProjectId, ownerProjectIdSql(operatorId))
                .ge(ArticleDraft::getCreatedAt, monthStart)));

        vo.setFailedDistributionTaskCount(distributionTaskMapper.selectCount(operatorTaskWrapper(operatorId)
                .eq(DistributionTask::getStatus, "failed")));
        vo.setRetryDistributionTaskCount(distributionTaskMapper.selectCount(operatorTaskWrapper(operatorId)
                .eq(DistributionTask::getStatus, "failed")
                .isNotNull(DistributionTask::getNextRetryAt)));
        vo.setSemiAutoTaskCount(distributionTaskMapper.selectCount(operatorTaskWrapper(operatorId)
                .eq(DistributionTask::getDispatchMode, "SEMI_AUTO")));
        vo.setInFlightExtensionTaskCount(distributionTaskMapper.selectCount(operatorTaskWrapper(operatorId)
                .eq(DistributionTask::getDispatchMode, "SEMI_AUTO")
                .in(DistributionTask::getStatus, IN_FLIGHT_EXTENSION_STATUSES)));
        vo.setCompletedDistributionTaskCount(distributionTaskMapper.selectCount(operatorTaskWrapper(operatorId)
                .in(DistributionTask::getStatus, COMPLETED_DISTRIBUTION_STATUSES)));
        return vo;
    }

    public ManagerWorkbenchOverviewVO managerOverview() {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("workbench.manager.read");

        ManagerWorkbenchOverviewVO vo = new ManagerWorkbenchOverviewVO();
        vo.setActiveUserCount(sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsActive, true)));
        vo.setActiveOperatorCount(sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "operator")
                .eq(SysUser::getIsActive, true)));
        vo.setPermissionCount(sysPermissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getStatus, List.of("active", "deprecated"))));
        vo.setAiPlatformConfigCount(aiPlatformConfigMapper.selectCount(new LambdaQueryWrapper<>()));
        vo.setPublishSiteCount(publishSiteMapper.selectCount(new LambdaQueryWrapper<>()));
        vo.setOpenSystemAlertCount(systemAlertMapper.selectCount(visibleSystemAlertWrapper(user)
                .eq(SystemAlert::getIsResolved, false)));
        vo.setHighSeveritySystemAlertCount(systemAlertMapper.selectCount(visibleSystemAlertWrapper(user)
                .eq(SystemAlert::getIsResolved, false)
                .in(SystemAlert::getSeverity, List.of("high", "critical"))));
        vo.setLatestSystemAlerts(loadLatestSystemAlerts(user));
        return vo;
    }

    public SuperAdminWorkbenchOverviewVO superAdminOverview() {
        SysUser user = currentUserService.requireCurrentUser();
        ensureWildcardUser(user);

        SuperAdminWorkbenchOverviewVO vo = new SuperAdminWorkbenchOverviewVO();
        vo.setTotalUserCount(sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()));
        vo.setActiveUserCount(sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsActive, true)));
        vo.setTotalCompanyCount(companyMapper.selectCount(new LambdaQueryWrapper<Company>()
                .isNull(Company::getDeletedAt)));
        vo.setTotalProjectCount(projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                .isNull(Project::getDeletedAt)));
        vo.setNullOwnerCompanyCount(companyMapper.selectCount(new LambdaQueryWrapper<Company>()
                .isNull(Company::getDeletedAt)
                .isNull(Company::getOwnerId)));
        vo.setDeprecatedEffectivePermissionCount(sysPermissionMapper.countDeprecatedBoundPermissions());
        vo.setOpenSystemAlertCount(systemAlertMapper.selectCount(new LambdaQueryWrapper<SystemAlert>()
                .eq(SystemAlert::getIsResolved, false)));
        return vo;
    }

    private List<SystemAlertTodoVO> loadLatestSystemAlerts(SysUser user) {
        Page<SystemAlert> page = systemAlertMapper.selectPage(
                new Page<>(1, 5),
                visibleSystemAlertWrapper(user)
                        .eq(SystemAlert::getIsResolved, false)
                        .orderByDesc(SystemAlert::getCreatedAt)
        );
        return page.getRecords().stream().map(this::toSystemAlertTodoVO).toList();
    }

    private LambdaQueryWrapper<SystemAlert> visibleSystemAlertWrapper(SysUser user) {
        return new LambdaQueryWrapper<SystemAlert>()
                .and(wrapper -> wrapper.eq(SystemAlert::getRecipientUserId, user.getId())
                        .or()
                        .eq(SystemAlert::getRecipientRole, user.getRole()));
    }

    private SystemAlertTodoVO toSystemAlertTodoVO(SystemAlert alert) {
        SystemAlertTodoVO vo = new SystemAlertTodoVO();
        vo.setId(alert.getId());
        vo.setAlertType(alert.getAlertType());
        vo.setSeverity(alert.getSeverity());
        vo.setSource(alert.getSource());
        vo.setMessage(alert.getMessage());
        vo.setContextJson(alert.getContextJson());
        vo.setCreatedAt(alert.getCreatedAt());
        return vo;
    }

    private LambdaQueryWrapper<Project> ownerProjectWrapper(Long ownerId) {
        return new LambdaQueryWrapper<Project>()
                .isNull(Project::getDeletedAt)
                .inSql(Project::getCompanyId, ownerCompanyIdSql(ownerId));
    }

    private LambdaQueryWrapper<DistributionTask> operatorTaskWrapper(Long operatorId) {
        return new LambdaQueryWrapper<DistributionTask>()
                .eq(DistributionTask::getOperatorId, operatorId);
    }

    private String ownerCompanyIdSql(Long ownerId) {
        // ownerId comes from the authenticated operator user id; do not pass external request input here.
        return "select id from company where deleted_at is null and owner_id = " + ownerId;
    }

    private String ownerProjectIdSql(Long ownerId) {
        // ownerId comes from the authenticated operator user id; do not pass external request input here.
        return "select p.id from project p join company c on c.id = p.company_id "
                + "where p.deleted_at is null and c.deleted_at is null and c.owner_id = " + ownerId;
    }

    private void ensureWildcardUser(SysUser user) {
        if (!permissionService.listPermKeys(user).contains("*")) {
            throw new BizException(403, "No permission: *");
        }
    }
}
