package com.huanjing.geo.module.workbench.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.huanjing.geo.common.exception.BizException;
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
import com.huanjing.geo.module.project.service.ProjectFlowPolicy;
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
import com.huanjing.geo.module.workbench.dto.SalesWorkbenchOverviewVO;
import com.huanjing.geo.module.workbench.dto.SuperAdminWorkbenchOverviewVO;
import com.huanjing.geo.module.workbench.dto.WorkbenchRiskGroupVO;
import com.huanjing.geo.module.workbench.dto.WorkbenchTodoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkbenchService {

    private static final Set<String> COMPLETED_DISTRIBUTION_STATUSES = Set.of("submitted", "confirmed", "published");
    private static final Set<String> IN_FLIGHT_EXTENSION_STATUSES = Set.of("token_issued", "filling", "filled");
    private static final Set<String> PRESALE_IN_FLIGHT_STATUSES = Set.of("INIT", "QUEUED", "RUNNING");
    private static final Set<String> HIGH_SEVERITIES = Set.of("high", "critical", "error");

    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final CompanyMapper companyMapper;
    private final BrandMapper brandMapper;
    private final PresaleReportMapper presaleReportMapper;
    private final ProjectMapper projectMapper;
    private final ReportMapper reportMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final BatchArticleGenerationTaskMapper batchArticleGenerationTaskMapper;
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
                .in(Project::getStatus, ProjectFlowPolicy.DELIVERY_PROGRESS_STATUS_SET)));
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
        Long systemTodoCount = systemAlertMapper.selectCount(visibleSystemAlertWrapper(user)
                .eq(SystemAlert::getIsResolved, false));
        Long systemHighTodoCount = systemAlertMapper.selectCount(visibleSystemAlertWrapper(user)
                .eq(SystemAlert::getIsResolved, false)
                .in(SystemAlert::getSeverity, HIGH_SEVERITIES));
        Long distributionTodoCount = countOperatorDistributionTodos(operatorId);
        Long distributionHighTodoCount = countOperatorHighDistributionTodos(operatorId);
        Long generationTodoCount = countOperatorGenerationTodos(operatorId);
        vo.setOpenTodoCount(safeLong(systemTodoCount) + safeLong(distributionTodoCount) + safeLong(generationTodoCount));
        vo.setHighSeverityTodoCount(safeLong(systemHighTodoCount) + safeLong(distributionHighTodoCount) + safeLong(generationTodoCount));
        List<WorkbenchTodoVO> todos = loadOperatorPriorityTodos(user, 8);
        vo.setPriorityTodos(todos);
        vo.setCustomerRiskGroups(buildRiskGroups(todos));
        return vo;
    }

    public SalesWorkbenchOverviewVO salesOverview() {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("workbench.sales.read");
        Long salesId = user.getId();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        SalesWorkbenchOverviewVO vo = new SalesWorkbenchOverviewVO();
        vo.setCustomerCount(companyMapper.selectCount(salesCompanyWrapper(salesId)));
        vo.setSignedCustomerCount(companyMapper.selectCount(salesCompanyWrapper(salesId)
                .eq(Company::getStatus, "signed")));
        vo.setPotentialCustomerCount(companyMapper.selectCount(salesCompanyWrapper(salesId)
                .eq(Company::getStatus, "potential")));
        vo.setReportCount(presaleReportMapper.selectCount(salesReportWrapper(salesId)));
        vo.setMonthlyReportCount(presaleReportMapper.selectCount(salesReportWrapper(salesId)
                .ge(PresaleReport::getCreatedAt, monthStart)));
        vo.setGeneratingReportCount(countSalesReportsByStatus(salesId, PRESALE_IN_FLIGHT_STATUSES));
        vo.setDoneReportCount(countSalesReportsByStatus(salesId, Set.of("DONE")));
        vo.setFailedReportCount(countSalesReportsByStatus(salesId, Set.of("FAILED")));
        List<WorkbenchTodoVO> todos = loadSalesPriorityTodos(salesId, 8);
        vo.setOpenTodoCount((long) todos.size());
        vo.setHighSeverityTodoCount(todos.stream()
                .filter(todo -> HIGH_SEVERITIES.contains(normalize(todo.getSeverity())))
                .count());
        vo.setPriorityTodos(todos);
        vo.setCustomerRiskGroups(buildRiskGroups(todos));
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
                .in(SystemAlert::getSeverity, HIGH_SEVERITIES)));
        vo.setLatestSystemAlerts(loadLatestSystemAlerts(user));
        List<WorkbenchTodoVO> todos = loadPriorityTodos(user, 10);
        vo.setPriorityTodos(todos);
        vo.setCustomerRiskGroups(buildRiskGroups(todos));
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

    private List<WorkbenchTodoVO> loadPriorityTodos(SysUser user, long size) {
        long fetchSize = Math.max(size * 3, 20);
        Page<SystemAlert> page = systemAlertMapper.selectPage(
                new Page<>(1, fetchSize),
                visibleSystemAlertWrapper(user)
                        .eq(SystemAlert::getIsResolved, false)
                        .orderByDesc(SystemAlert::getCreatedAt)
        );
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        return page.getRecords().stream()
                .map(this::toWorkbenchTodo)
                .sorted(Comparator.comparingInt(this::severityRank).thenComparing(
                        WorkbenchTodoVO::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(Math.max(size, 1))
                .toList();
    }

    private List<WorkbenchTodoVO> loadOperatorPriorityTodos(SysUser user, long size) {
        List<WorkbenchTodoVO> todos = new ArrayList<>(loadPriorityTodos(user, size));
        todos.addAll(loadOperatorDistributionTodos(user.getId(), Math.max(size * 2, 12)));
        todos.addAll(loadOperatorGenerationTodos(user.getId(), Math.max(size * 2, 12)));
        return todos.stream()
                .sorted(Comparator.comparingInt(this::severityRank).thenComparing(
                        WorkbenchTodoVO::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(Math.max(size, 1))
                .toList();
    }

    private List<WorkbenchTodoVO> loadOperatorDistributionTodos(Long operatorId, long size) {
        Page<DistributionTask> page = distributionTaskMapper.selectPage(
                new Page<>(1, Math.max(size, 1)),
                operatorDistributionTodoWrapper(operatorId)
                        .orderByDesc(DistributionTask::getCreatedAt)
        );
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return List.of();
        }
        List<DistributionTask> tasks = page.getRecords();
        Map<Long, ArticleDraft> articleMap = loadArticleMap(tasks);
        Map<Long, Project> projectMap = loadProjectMap(tasks);
        Map<Long, Brand> brandMap = loadBrandMap(tasks, projectMap);
        Map<Long, Company> companyMap = loadCompanyMap(projectMap, brandMap);
        return tasks.stream()
                .map(task -> toDistributionTodo(task, articleMap, projectMap, brandMap, companyMap))
                .toList();
    }

    private Long countOperatorDistributionTodos(Long operatorId) {
        return distributionTaskMapper.selectCount(operatorDistributionTodoWrapper(operatorId));
    }

    private Long countOperatorHighDistributionTodos(Long operatorId) {
        return distributionTaskMapper.selectCount(operatorTaskWrapper(operatorId)
                .eq(DistributionTask::getStatus, "failed"));
    }

    private List<WorkbenchTodoVO> loadOperatorGenerationTodos(Long operatorId, long size) {
        Page<BatchArticleGenerationTask> page = batchArticleGenerationTaskMapper.selectPage(
                new Page<>(1, Math.max(size, 1)),
                operatorGenerationTodoWrapper(operatorId)
                        .orderByDesc(BatchArticleGenerationTask::getUpdatedAt, BatchArticleGenerationTask::getCreatedAt)
        );
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return List.of();
        }
        List<BatchArticleGenerationTask> tasks = page.getRecords();
        Map<Long, Project> projectMap = loadProjectMapFromGenerationTasks(tasks);
        Map<Long, Brand> brandMap = loadBrandMapFromProjects(projectMap);
        Map<Long, Company> companyMap = loadCompanyMap(projectMap, brandMap);
        return tasks.stream()
                .map(task -> toGenerationTodo(task, projectMap, brandMap, companyMap))
                .toList();
    }

    private Long countOperatorGenerationTodos(Long operatorId) {
        return batchArticleGenerationTaskMapper.selectCount(operatorGenerationTodoWrapper(operatorId));
    }

    private List<WorkbenchTodoVO> loadSalesPriorityTodos(Long salesId, long size) {
        Page<PresaleReport> page = presaleReportMapper.selectPage(
                new Page<>(1, Math.max(size, 1)),
                salesReportWrapper(salesId)
                        .eq(PresaleReport::getStatus, "FAILED")
                        .orderByDesc(PresaleReport::getUpdatedAt, PresaleReport::getCreatedAt)
        );
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        return page.getRecords().stream()
                .map(this::toPresaleReportTodo)
                .toList();
    }

    private List<WorkbenchRiskGroupVO> buildRiskGroups(List<WorkbenchTodoVO> todos) {
        if (todos == null || todos.isEmpty()) {
            return List.of();
        }
        Map<String, List<WorkbenchTodoVO>> grouped = todos.stream()
                .collect(Collectors.groupingBy(
                        todo -> riskGroupKey(todo.getCustomerName(), todo.getBrandName()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<WorkbenchRiskGroupVO> groups = new ArrayList<>();
        for (List<WorkbenchTodoVO> items : grouped.values()) {
            WorkbenchTodoVO first = items.get(0);
            WorkbenchRiskGroupVO group = new WorkbenchRiskGroupVO();
            group.setCustomerName(first.getCustomerName());
            group.setBrandName(first.getBrandName());
            group.setRiskCount((long) items.size());
            group.setHighSeverityCount(items.stream()
                    .filter(item -> HIGH_SEVERITIES.contains(normalize(item.getSeverity())))
                    .count());
            group.setLatestMessage(items.stream()
                    .max(Comparator.comparing(
                            WorkbenchTodoVO::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .map(WorkbenchTodoVO::getMessage)
                    .orElse(null));
            group.setTodos(items);
            groups.add(group);
        }
        return groups.stream()
                .sorted(Comparator.comparing(WorkbenchRiskGroupVO::getHighSeverityCount, Comparator.reverseOrder())
                        .thenComparing(WorkbenchRiskGroupVO::getRiskCount, Comparator.reverseOrder()))
                .toList();
    }

    private WorkbenchTodoVO toWorkbenchTodo(SystemAlert alert) {
        JSONObject context = parseContext(alert.getContextJson());
        WorkbenchTodoVO vo = new WorkbenchTodoVO();
        vo.setId(alert.getId());
        vo.setSourceType("system_alert");
        vo.setAlertType(alert.getAlertType());
        vo.setSeverity(alert.getSeverity());
        vo.setMessage(alert.getMessage());
        vo.setCustomerName(context == null ? null : context.getStr("companyName"));
        vo.setBrandName(context == null ? null : context.getStr("brandName"));
        vo.setRoute(context == null ? null : context.getStr("route"));
        vo.setCreatedAt(alert.getCreatedAt());
        return vo;
    }

    private WorkbenchTodoVO toDistributionTodo(DistributionTask task,
                                               Map<Long, ArticleDraft> articleMap,
                                               Map<Long, Project> projectMap,
                                               Map<Long, Brand> brandMap,
                                               Map<Long, Company> companyMap) {
        ArticleDraft article = articleMap.get(task.getArticleId());
        Project project = projectMap.get(task.getProjectId());
        Brand brand = resolveBrand(task, project, brandMap);
        Company company = resolveCompany(project, brand, companyMap);

        WorkbenchTodoVO vo = new WorkbenchTodoVO();
        vo.setId(task.getId());
        vo.setSourceType("distribution_task");
        vo.setAlertType(distributionAlertType(task));
        vo.setSeverity(distributionSeverity(task));
        vo.setMessage(distributionMessage(task, article));
        vo.setCustomerName(company == null ? projectCompanyName(project) : company.getCompanyName());
        vo.setBrandName(brand == null ? projectBrandName(project) : brand.getBrandName());
        vo.setRoute("/admin/content/execution");
        vo.setCreatedAt(task.getCreatedAt());
        return vo;
    }

    private WorkbenchTodoVO toGenerationTodo(BatchArticleGenerationTask task,
                                             Map<Long, Project> projectMap,
                                             Map<Long, Brand> brandMap,
                                             Map<Long, Company> companyMap) {
        Project project = projectMap.get(task.getProjectId());
        Brand brand = project != null && project.getBrandId() != null ? brandMap.get(project.getBrandId()) : null;
        Company company = resolveCompany(project, brand, companyMap);

        WorkbenchTodoVO vo = new WorkbenchTodoVO();
        vo.setId(task.getId());
        vo.setSourceType("article_generation_task");
        vo.setAlertType("ARTICLE_GENERATION_FAILED");
        vo.setSeverity("high");
        vo.setMessage("文章主题「" + topicText(task) + "」生成失败，请检查后重试");
        vo.setCustomerName(company == null ? projectCompanyName(project) : company.getCompanyName());
        vo.setBrandName(brand == null ? projectBrandName(project) : brand.getBrandName());
        vo.setRoute("/admin/content/execution");
        vo.setCreatedAt(firstTime(task.getUpdatedAt(), task.getCreatedAt()));
        return vo;
    }

    private WorkbenchTodoVO toPresaleReportTodo(PresaleReport report) {
        WorkbenchTodoVO vo = new WorkbenchTodoVO();
        vo.setId(report.getId());
        vo.setSourceType("presale_report");
        vo.setAlertType("PRESALE_REPORT_GENERATION_FAILED");
        vo.setSeverity("high");
        vo.setMessage("诊断报告「" + reportBrandName(report) + "」生成失败，请重新处理");
        vo.setBrandName(reportBrandName(report));
        vo.setRoute("/admin/presale/report");
        vo.setCreatedAt(firstTime(report.getUpdatedAt(), report.getCreatedAt()));
        return vo;
    }

    private LambdaQueryWrapper<SystemAlert> visibleSystemAlertWrapper(SysUser user) {
        LambdaQueryWrapper<SystemAlert> wrapper = new LambdaQueryWrapper<SystemAlert>();
        if (isSuperAdmin(user)) {
            return wrapper;
        }
        return wrapper.and(scope -> scope.eq(SystemAlert::getRecipientUserId, user.getId())
                .or()
                .eq(SystemAlert::getRecipientRole, user.getRole()));
    }

    private boolean isSuperAdmin(SysUser user) {
        return user != null
                && StringUtils.hasText(user.getRole())
                && "super_admin".equals(user.getRole().trim().toLowerCase(Locale.ROOT));
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

    private JSONObject parseContext(String contextJson) {
        if (!StringUtils.hasText(contextJson)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(contextJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String riskGroupKey(String customerName, String brandName) {
        return (StringUtils.hasText(customerName) ? customerName.trim() : "未知客户")
                + "::"
                + (StringUtils.hasText(brandName) ? brandName.trim() : "未知品牌");
    }

    private int severityRank(WorkbenchTodoVO todo) {
        return switch (normalize(todo.getSeverity())) {
            case "critical" -> 1;
            case "high", "error" -> 2;
            case "warn", "warning" -> 3;
            default -> 4;
        };
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : "";
    }

    private LambdaQueryWrapper<DistributionTask> operatorDistributionTodoWrapper(Long operatorId) {
        return operatorTaskWrapper(operatorId)
                .and(wrapper -> wrapper.eq(DistributionTask::getStatus, "failed")
                        .or(nested -> nested.eq(DistributionTask::getDispatchMode, "SEMI_AUTO")
                                .in(DistributionTask::getStatus, IN_FLIGHT_EXTENSION_STATUSES)));
    }

    private LambdaQueryWrapper<BatchArticleGenerationTask> operatorGenerationTodoWrapper(Long operatorId) {
        return new LambdaQueryWrapper<BatchArticleGenerationTask>()
                .inSql(BatchArticleGenerationTask::getProjectId, ownerProjectIdSql(operatorId))
                .eq(BatchArticleGenerationTask::getStatus, "failed");
    }

    private Map<Long, ArticleDraft> loadArticleMap(List<DistributionTask> tasks) {
        List<Long> ids = tasks.stream()
                .map(DistributionTask::getArticleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ArticleDraft> rows = articleDraftMapper.selectBatchIds(ids);
        if (rows == null) {
            return Map.of();
        }
        return rows.stream().collect(Collectors.toMap(ArticleDraft::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Project> loadProjectMap(List<DistributionTask> tasks) {
        List<Long> ids = tasks.stream()
                .map(DistributionTask::getProjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Project> rows = projectMapper.selectBatchIds(ids);
        if (rows == null) {
            return Map.of();
        }
        return rows.stream().collect(Collectors.toMap(Project::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Project> loadProjectMapFromGenerationTasks(List<BatchArticleGenerationTask> tasks) {
        List<Long> ids = tasks.stream()
                .map(BatchArticleGenerationTask::getProjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Project> rows = projectMapper.selectBatchIds(ids);
        if (rows == null) {
            return Map.of();
        }
        return rows.stream().collect(Collectors.toMap(Project::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Brand> loadBrandMap(List<DistributionTask> tasks, Map<Long, Project> projectMap) {
        List<Long> ids = new ArrayList<>();
        tasks.stream()
                .map(DistributionTask::getTargetBrandId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        projectMap.values().stream()
                .map(Project::getBrandId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        ids = ids.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Brand> rows = brandMapper.selectBatchIds(ids);
        if (rows == null) {
            return Map.of();
        }
        return rows.stream().collect(Collectors.toMap(Brand::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Brand> loadBrandMapFromProjects(Map<Long, Project> projectMap) {
        List<Long> ids = projectMap.values().stream()
                .map(Project::getBrandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Brand> rows = brandMapper.selectBatchIds(ids);
        if (rows == null) {
            return Map.of();
        }
        return rows.stream().collect(Collectors.toMap(Brand::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Company> loadCompanyMap(Map<Long, Project> projectMap, Map<Long, Brand> brandMap) {
        List<Long> ids = new ArrayList<>();
        projectMap.values().stream()
                .map(Project::getCompanyId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        brandMap.values().stream()
                .map(Brand::getCompanyId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        ids = ids.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Company> rows = companyMapper.selectBatchIds(ids);
        if (rows == null) {
            return Map.of();
        }
        return rows.stream().collect(Collectors.toMap(Company::getId, Function.identity(), (left, right) -> left));
    }

    private Brand resolveBrand(DistributionTask task, Project project, Map<Long, Brand> brandMap) {
        if (task.getTargetBrandId() != null && brandMap.containsKey(task.getTargetBrandId())) {
            return brandMap.get(task.getTargetBrandId());
        }
        if (project != null && project.getBrandId() != null) {
            return brandMap.get(project.getBrandId());
        }
        return null;
    }

    private Company resolveCompany(Project project, Brand brand, Map<Long, Company> companyMap) {
        if (project != null && project.getCompanyId() != null && companyMap.containsKey(project.getCompanyId())) {
            return companyMap.get(project.getCompanyId());
        }
        if (brand != null && brand.getCompanyId() != null) {
            return companyMap.get(brand.getCompanyId());
        }
        return null;
    }

    private String distributionAlertType(DistributionTask task) {
        if ("failed".equals(normalize(task.getStatus()))) {
            return task.getNextRetryAt() == null ? "DISTRIBUTION_FAILED" : "DISTRIBUTION_RETRY_PENDING";
        }
        return "SEMI_AUTO_DISTRIBUTION_PENDING";
    }

    private String distributionSeverity(DistributionTask task) {
        if ("failed".equals(normalize(task.getStatus()))) {
            return task.getNextRetryAt() == null ? "high" : "warn";
        }
        return "warn";
    }

    private String distributionMessage(DistributionTask task, ArticleDraft article) {
        String title = articleTitle(article);
        String status = normalize(task.getStatus());
        if ("failed".equals(status)) {
            if (task.getNextRetryAt() != null) {
                return "文章《" + title + "》发布失败，系统已安排重试，请关注处理结果";
            }
            return "文章《" + title + "》发布失败，请及时排查并处理";
        }
        if ("filled".equals(status)) {
            return "文章《" + title + "》半自动发布已填充完成，请确认发布结果";
        }
        if ("filling".equals(status)) {
            return "文章《" + title + "》半自动发布正在执行，请关注本地助手状态";
        }
        return "文章《" + title + "》半自动发布待处理，请打开内容执行继续处理";
    }

    private String articleTitle(ArticleDraft article) {
        if (article != null && StringUtils.hasText(article.getTitle())) {
            return article.getTitle().trim();
        }
        return "未命名文章";
    }

    private String projectCompanyName(Project project) {
        return project == null ? null : project.getCompanyName();
    }

    private String projectBrandName(Project project) {
        return project == null ? null : project.getBrandName();
    }

    private String topicText(BatchArticleGenerationTask task) {
        if (StringUtils.hasText(task.getTopic())) {
            return task.getTopic().trim();
        }
        if (StringUtils.hasText(task.getTopicAsQuestion())) {
            return task.getTopicAsQuestion().trim();
        }
        return "未命名主题";
    }

    private String reportBrandName(PresaleReport report) {
        if (report != null && StringUtils.hasText(report.getBrandName())) {
            return report.getBrandName().trim();
        }
        return "未命名品牌";
    }

    private LocalDateTime firstTime(LocalDateTime preferred, LocalDateTime fallback) {
        return preferred == null ? fallback : preferred;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
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

    private LambdaQueryWrapper<Company> salesCompanyWrapper(Long salesId) {
        return new LambdaQueryWrapper<Company>()
                .isNull(Company::getDeletedAt)
                .eq(Company::getSalesOwnerId, salesId);
    }

    private LambdaQueryWrapper<PresaleReport> salesReportWrapper(Long salesId) {
        return new LambdaQueryWrapper<PresaleReport>()
                .isNull(PresaleReport::getDeletedAt)
                .eq(PresaleReport::getCreatedBy, salesId);
    }

    private Long countSalesReportsByStatus(Long salesId, Set<String> statuses) {
        return presaleReportMapper.selectCount(salesReportWrapper(salesId)
                .in(PresaleReport::getStatus, statuses));
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
