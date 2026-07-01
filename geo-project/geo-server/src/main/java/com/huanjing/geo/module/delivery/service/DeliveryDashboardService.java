package com.huanjing.geo.module.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.delivery.dto.DeliveryExceptionVO;
import com.huanjing.geo.module.delivery.dto.DeliveryOperatorStatsVO;
import com.huanjing.geo.module.delivery.dto.DeliveryOverviewVO;
import com.huanjing.geo.module.dispatch.entity.DispatchAlert;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.mapper.DispatchAlertMapper;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.dispatch.service.DispatchAlertService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.service.ProjectFlowPolicy;
import com.huanjing.geo.module.report.entity.Report;
import com.huanjing.geo.module.report.mapper.ReportMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliveryDashboardService {

    private static final Set<String> FAILED_TASK_STATUSES = Set.of("failed", "dead_letter");

    private final CurrentUserService currentUserService;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final ReportMapper reportMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final DispatchAlertMapper dispatchAlertMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchAlertService dispatchAlertService;
    private final SysUserMapper sysUserMapper;

    public DeliveryOverviewVO overview() {
        currentUserService.ensurePermission("delivery.overview.read");
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        DeliveryOverviewVO vo = new DeliveryOverviewVO();
        vo.setTotalCustomers(companyMapper.selectCount(new LambdaQueryWrapper<Company>().isNull(Company::getDeletedAt)));
        vo.setActiveProjects(projectMapper.selectCount(activeProjectWrapper(null)));
        vo.setHighRiskProjects(projectMapper.selectCount(projectScopeWrapper(null).eq(Project::getStage, "high_risk")));
        vo.setOpenExceptions(dispatchAlertMapper.selectCount(new LambdaQueryWrapper<DispatchAlert>().eq(DispatchAlert::getStatus, "open")));
        vo.setFailedDispatchTasks(dispatchTaskMapper.selectCount(new LambdaQueryWrapper<DispatchTask>()
                .in(DispatchTask::getStatus, FAILED_TASK_STATUSES)));
        vo.setMonthlyReports(reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .ge(Report::getCreatedAt, monthStart)));
        vo.setMonthlyArticles(articleDraftMapper.selectCount(new LambdaQueryWrapper<ArticleDraft>()
                .ge(ArticleDraft::getCreatedAt, monthStart)));
        vo.setActiveOperators(countActiveOperators());
        return vo;
    }

    public List<DeliveryOperatorStatsVO> operatorStats() {
        currentUserService.ensurePermission("delivery.operator_stats.read");
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<SysUser> operators = activeOperators();
        if (operators.isEmpty()) {
            return List.of();
        }

        return operators.stream()
                .map(operator -> toOperatorStats(operator, monthStart))
                .sorted((a, b) -> Long.compare(score(b), score(a)))
                .toList();
    }

    public Page<DeliveryExceptionVO> exceptions(long current, long size, String severity, String status) {
        currentUserService.ensurePermission("delivery.overview.read");
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.max(1, Math.min(size, 100));
        Page<DispatchAlert> page = dispatchAlertMapper.selectPage(
                new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<DispatchAlert>()
                        .eq(severity != null && !severity.isBlank(), DispatchAlert::getSeverity, severity)
                        .eq(status != null && !status.isBlank(), DispatchAlert::getStatus, status)
                        .orderByDesc(DispatchAlert::getCreatedAt)
        );

        Map<Long, Project> projectMap = loadProjectMap(page.getRecords().stream()
                .map(DispatchAlert::getProjectId)
                .filter(Objects::nonNull)
                .toList());
        Map<Long, Company> companyMap = loadCompanyMap(projectMap.values().stream()
                .map(Project::getCompanyId)
                .filter(Objects::nonNull)
                .toList());
        Map<Long, SysUser> ownerMap = loadUserMap(companyMap.values().stream()
                .map(Company::getOwnerId)
                .filter(Objects::nonNull)
                .toList());

        Page<DeliveryExceptionVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(alert -> toExceptionVO(alert, projectMap, companyMap, ownerMap))
                .toList());
        return result;
    }

    public void handleException(Long alertId, String note) {
        currentUserService.ensurePermission("delivery.exception.handle");
        SysUser user = currentUserService.requireCurrentUser();
        dispatchAlertService.resolveAlert(alertId, user.getId(), note);
    }

    private DeliveryOperatorStatsVO toOperatorStats(SysUser operator, LocalDateTime monthStart) {
        Long operatorId = operator.getId();
        DeliveryOperatorStatsVO vo = new DeliveryOperatorStatsVO();
        vo.setOperatorId(operatorId);
        vo.setOperatorName(displayName(operator));
        vo.setCustomerCount(companyMapper.selectCount(new LambdaQueryWrapper<Company>()
                .isNull(Company::getDeletedAt)
                .eq(Company::getOwnerId, operatorId)));
        vo.setActiveProjectCount(projectMapper.selectCount(activeProjectWrapper(operatorId)));
        vo.setHighRiskProjectCount(projectMapper.selectCount(projectScopeWrapper(operatorId)
                .eq(Project::getStage, "high_risk")));
        vo.setMonthlyReportCount(reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .inSql(Report::getProjectId, ownerProjectIdSql(operatorId))
                .ge(Report::getCreatedAt, monthStart)));
        vo.setMonthlyArticleCount(articleDraftMapper.selectCount(new LambdaQueryWrapper<ArticleDraft>()
                .inSql(ArticleDraft::getProjectId, ownerProjectIdSql(operatorId))
                .ge(ArticleDraft::getCreatedAt, monthStart)));
        vo.setOpenExceptionCount(dispatchAlertMapper.selectCount(new LambdaQueryWrapper<DispatchAlert>()
                .inSql(DispatchAlert::getProjectId, ownerProjectIdSql(operatorId))
                .eq(DispatchAlert::getStatus, "open")));
        vo.setFailedDispatchTaskCount(dispatchTaskMapper.selectCount(new LambdaQueryWrapper<DispatchTask>()
                .inSql(DispatchTask::getProjectId, ownerProjectIdSql(operatorId))
                .in(DispatchTask::getStatus, FAILED_TASK_STATUSES)));
        return vo;
    }

    private LambdaQueryWrapper<Project> activeProjectWrapper(Long ownerId) {
        return projectScopeWrapper(ownerId).in(Project::getStatus, ProjectFlowPolicy.DELIVERY_PROGRESS_STATUS_SET);
    }

    private LambdaQueryWrapper<Project> projectScopeWrapper(Long ownerId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>().isNull(Project::getDeletedAt);
        if (ownerId != null) {
            wrapper.inSql(Project::getCompanyId, ownerCompanyIdSql(ownerId));
        }
        return wrapper;
    }

    private String ownerCompanyIdSql(Long ownerId) {
        return "select id from company where deleted_at is null and owner_id = " + ownerId;
    }

    private String ownerProjectIdSql(Long ownerId) {
        return "select p.id from project p join company c on c.id = p.company_id "
                + "where p.deleted_at is null and c.deleted_at is null and c.owner_id = " + ownerId;
    }

    private long score(DeliveryOperatorStatsVO vo) {
        return safe(vo.getActiveProjectCount()) * 100
                + safe(vo.getOpenExceptionCount()) * 20
                + safe(vo.getFailedDispatchTaskCount()) * 10
                + safe(vo.getMonthlyArticleCount());
    }

    private long safe(Long value) {
        return value == null ? 0 : value;
    }

    private List<SysUser> activeOperators() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "operator")
                .eq(SysUser::getIsActive, true)
                .orderByAsc(SysUser::getId));
    }

    private long countActiveOperators() {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "operator")
                .eq(SysUser::getIsActive, true));
        return count == null ? 0L : count;
    }

    private Map<Long, Project> loadProjectMap(List<Long> projectIds) {
        List<Long> ids = distinctIds(projectIds);
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return projectMapper.selectList(new LambdaQueryWrapper<Project>().in(Project::getId, ids))
                .stream()
                .collect(Collectors.toMap(Project::getId, item -> item, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, Company> loadCompanyMap(List<Long> companyIds) {
        List<Long> ids = distinctIds(companyIds);
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return companyMapper.selectList(new LambdaQueryWrapper<Company>().in(Company::getId, ids))
                .stream()
                .collect(Collectors.toMap(Company::getId, item -> item, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, SysUser> loadUserMap(List<Long> userIds) {
        List<Long> ids = distinctIds(userIds);
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getId, ids))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, item -> item, (a, b) -> a, LinkedHashMap::new));
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private DeliveryExceptionVO toExceptionVO(DispatchAlert alert,
                                              Map<Long, Project> projectMap,
                                              Map<Long, Company> companyMap,
                                              Map<Long, SysUser> ownerMap) {
        Project project = projectMap.get(alert.getProjectId());
        Company company = project == null ? null : companyMap.get(project.getCompanyId());
        SysUser owner = company == null ? null : ownerMap.get(company.getOwnerId());

        DeliveryExceptionVO vo = new DeliveryExceptionVO();
        vo.setId(alert.getId());
        vo.setAlertCode(alert.getAlertCode());
        vo.setTaskId(alert.getTaskId());
        vo.setProjectId(alert.getProjectId());
        vo.setProjectName(project == null ? null : project.getProjectName());
        vo.setOwnerId(company == null ? null : company.getOwnerId());
        vo.setOwnerName(owner == null ? null : displayName(owner));
        vo.setSeverity(alert.getSeverity());
        vo.setStatus(alert.getStatus());
        vo.setTitle(alert.getTitle());
        vo.setContent(alert.getContent());
        vo.setRetryCount(alert.getRetryCount());
        vo.setContextJson(alert.getContextJson());
        vo.setResolvedAt(alert.getResolvedAt());
        vo.setResolvedBy(alert.getResolvedBy());
        vo.setCreatedAt(alert.getCreatedAt());
        return vo;
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getUsername();
    }
}
