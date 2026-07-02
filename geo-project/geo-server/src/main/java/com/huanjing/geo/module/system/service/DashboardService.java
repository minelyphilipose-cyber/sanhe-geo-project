package com.huanjing.geo.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.dispatch.entity.DispatchAlert;
import com.huanjing.geo.module.dispatch.mapper.DispatchAlertMapper;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.report.entity.Report;
import com.huanjing.geo.module.report.mapper.ReportMapper;
import com.huanjing.geo.module.system.dto.DashboardOverviewVO;
import com.huanjing.geo.module.system.dto.PendingItemVO;
import com.huanjing.geo.module.system.dto.ProjectStageDistributionVO;
import com.huanjing.geo.module.system.dto.ReportTrendVO;
import com.huanjing.geo.module.system.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Set<String> ACTIVE_STAGES = Set.of(
            "collecting_materials",
            "baseline_diagnosis",
            "executing",
            "needs_renewal",
            "high_risk",
            "dispute_handling"
    );

    private static final Set<String> VALID_STAGES = Set.of(
            "pending_start",
            "collecting_materials",
            "baseline_diagnosis",
            "executing",
            "needs_renewal",
            "high_risk",
            "dispute_handling",
            "completed"
    );

    private static final Map<String, String> STAGE_LABELS = Map.ofEntries(
            Map.entry("pending_start", "待启动"),
            Map.entry("collecting_materials", "资料收集中"),
            Map.entry("baseline_diagnosis", "基线诊断中"),
            Map.entry("executing", "执行中"),
            Map.entry("biweekly_feedback", "双周反馈"),
            Map.entry("monthly_report", "月报阶段"),
            Map.entry("quarterly_report", "季报阶段"),
            Map.entry("needs_renewal", "需续费"),
            Map.entry("high_risk", "高风险"),
            Map.entry("dispute_handling", "争议处理中"),
            Map.entry("completed", "已结束")
    );

    private final CurrentUserService currentUserService;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final ReportMapper reportMapper;
    private final PresaleReportVersionMapper presaleReportVersionMapper;
    private final PartnerMapper partnerMapper;
    private final DispatchAlertMapper dispatchAlertMapper;

    public DashboardOverviewVO overview() {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Long scopePartnerId = currentUserService.requirePartnerScope(user);

        DashboardOverviewVO vo = new DashboardOverviewVO();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        LambdaQueryWrapper<Company> companyWrapper = new LambdaQueryWrapper<Company>()
                .isNull(Company::getDeletedAt);
        if (scopePartnerId != null) {
            companyWrapper.eq(Company::getPartnerId, scopePartnerId);
        }
        vo.setTotalCustomers(companyMapper.selectCount(companyWrapper));

        LambdaQueryWrapper<Project> activeProjectWrapper = new LambdaQueryWrapper<Project>()
                .isNull(Project::getDeletedAt)
                .in(Project::getStage, ACTIVE_STAGES);
        if (scopePartnerId != null) {
            activeProjectWrapper.eq(Project::getPartnerId, scopePartnerId);
        }
        vo.setActiveProjects(projectMapper.selectCount(activeProjectWrapper));

        LambdaQueryWrapper<Project> allProjectWrapper = new LambdaQueryWrapper<Project>()
                .isNull(Project::getDeletedAt);
        if (scopePartnerId != null) {
            allProjectWrapper.eq(Project::getPartnerId, scopePartnerId);
        }
        vo.setTotalProjects(projectMapper.selectCount(allProjectWrapper));

        LambdaQueryWrapper<Report> reportWrapper = new LambdaQueryWrapper<Report>()
                .ge(Report::getCreatedAt, monthStart)
                .notIn(Report::getReportType, List.of("biweekly", "monthly", "quarterly"));
        Long monthlyDiagnosisReports = countMonthlyPresaleReports(user, monthStart);
        vo.setMonthlyDiagnosisReports(monthlyDiagnosisReports);

        if (scopePartnerId != null) {
            List<Long> projectIds = loadProjectIdsByPartner(scopePartnerId);
            if (projectIds.isEmpty()) {
                vo.setMonthlyReports(monthlyDiagnosisReports);
            } else {
                reportWrapper.in(Report::getProjectId, projectIds);
                vo.setMonthlyReports(reportMapper.selectCount(reportWrapper) + monthlyDiagnosisReports);
            }
        } else {
            vo.setMonthlyReports(reportMapper.selectCount(reportWrapper) + monthlyDiagnosisReports);
        }

        if (scopePartnerId == null) {
            LocalDateTime last7Start = LocalDate.now().minusDays(6).atStartOfDay();
            Long openAlerts = dispatchAlertMapper.selectCount(
                    new LambdaQueryWrapper<DispatchAlert>()
                            .eq(DispatchAlert::getStatus, "open")
                            .ge(DispatchAlert::getCreatedAt, last7Start)
            );
            vo.setOpenAlerts(openAlerts);
            vo.setTotalPartners(partnerMapper.selectCount(new LambdaQueryWrapper<>()));
        } else {
            vo.setOpenAlerts(0L);
            vo.setTotalPartners(null);
        }

        LambdaQueryWrapper<Company> newCustomerWrapper = new LambdaQueryWrapper<Company>()
                .isNull(Company::getDeletedAt)
                .ge(Company::getCreatedAt, monthStart)
                .eq(Company::getStatus, "signed");
        if (scopePartnerId != null) {
            newCustomerWrapper.eq(Company::getPartnerId, scopePartnerId);
        }
        vo.setMonthlyNewCustomers(companyMapper.selectCount(newCustomerWrapper));

        LambdaQueryWrapper<Project> riskWrapper = new LambdaQueryWrapper<Project>()
                .isNull(Project::getDeletedAt)
                .eq(Project::getStage, "high_risk");
        if (scopePartnerId != null) {
            riskWrapper.eq(Project::getPartnerId, scopePartnerId);
        }
        vo.setHighRiskProjects(projectMapper.selectCount(riskWrapper));
        return vo;
    }

    public List<PendingItemVO> pendingItems(int limit) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Long scopePartnerId = currentUserService.requirePartnerScope(user);

        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<PendingItemVO> items = new ArrayList<>();

        List<Long> scopedProjectIds = scopePartnerId == null ? List.of() : loadProjectIdsByPartner(scopePartnerId);

        LambdaQueryWrapper<Report> draftReportWrapper = new LambdaQueryWrapper<Report>()
                .eq(Report::getStatus, "draft")
                .notIn(Report::getReportType, List.of("biweekly", "monthly", "quarterly"))
                .orderByDesc(Report::getCreatedAt)
                .last("LIMIT " + safeLimit);
        if (scopePartnerId != null) {
            if (scopedProjectIds.isEmpty()) {
                draftReportWrapper.eq(Report::getId, -1L);
            } else {
                draftReportWrapper.in(Report::getProjectId, scopedProjectIds);
            }
        }
        List<Report> draftReports = reportMapper.selectList(draftReportWrapper);
        for (Report report : draftReports) {
            PendingItemVO item = new PendingItemVO();
            item.setType("report_review");
            item.setTitle("报表待复核");
            item.setDescription("报表 #" + report.getId() + "（" + report.getReportType() + "）待处理");
            item.setTargetPath("/admin/reports/" + report.getId());
            item.setTargetId(report.getId());
            item.setCreatedAt(report.getCreatedAt());
            item.setPriority("high");
            items.add(item);
        }

        LambdaQueryWrapper<Project> riskProjectWrapper = new LambdaQueryWrapper<Project>()
                .isNull(Project::getDeletedAt)
                .eq(Project::getStage, "high_risk")
                .orderByDesc(Project::getUpdatedAt)
                .last("LIMIT " + safeLimit);
        if (scopePartnerId != null) {
            riskProjectWrapper.eq(Project::getPartnerId, scopePartnerId);
        }
        List<Project> riskProjects = projectMapper.selectList(riskProjectWrapper);
        for (Project project : riskProjects) {
            PendingItemVO item = new PendingItemVO();
            item.setType("high_risk_project");
            item.setTitle("高风险项目");
            item.setDescription("项目「" + project.getProjectName() + "」处于高风险状态");
            item.setTargetPath("/admin/projects/" + project.getId());
            item.setTargetId(project.getId());
            item.setCreatedAt(project.getUpdatedAt());
            item.setPriority("high");
            items.add(item);
        }

        if (scopePartnerId == null) {
            LocalDateTime last7Start = LocalDate.now().minusDays(6).atStartOfDay();
            LambdaQueryWrapper<DispatchAlert> alertWrapper = new LambdaQueryWrapper<DispatchAlert>()
                    .eq(DispatchAlert::getStatus, "open")
                    .ge(DispatchAlert::getCreatedAt, last7Start)
                    .orderByDesc(DispatchAlert::getCreatedAt)
                    .last("LIMIT " + safeLimit);
            List<DispatchAlert> alerts = dispatchAlertMapper.selectList(alertWrapper);
            for (DispatchAlert alert : alerts) {
                PendingItemVO item = new PendingItemVO();
                item.setType("system_alert");
                item.setTitle("系统告警");
                item.setDescription(alert.getTitle() != null ? alert.getTitle()
                        : (alert.getContent() != null ? alert.getContent() : "告警 #" + alert.getId()));
                item.setTargetPath("/admin/alerts");
                item.setTargetId(alert.getId());
                item.setCreatedAt(alert.getCreatedAt());
                item.setPriority("medium");
                items.add(item);
            }
        }

        LambdaQueryWrapper<Project> renewalWrapper = new LambdaQueryWrapper<Project>()
                .isNull(Project::getDeletedAt)
                .eq(Project::getStage, "needs_renewal")
                .orderByAsc(Project::getEndDate)
                .last("LIMIT " + safeLimit);
        if (scopePartnerId != null) {
            renewalWrapper.eq(Project::getPartnerId, scopePartnerId);
        }
        List<Project> renewalProjects = projectMapper.selectList(renewalWrapper);
        for (Project project : renewalProjects) {
            PendingItemVO item = new PendingItemVO();
            item.setType("pending_renewal");
            item.setTitle("待续费项目");
            item.setDescription("项目「" + project.getProjectName() + "」即将到期，请跟进续费");
            item.setTargetPath("/admin/projects/" + project.getId());
            item.setTargetId(project.getId());
            item.setCreatedAt(project.getUpdatedAt());
            item.setPriority("medium");
            items.add(item);
        }

        items.sort(Comparator.comparing(PendingItemVO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        if (items.size() > safeLimit) {
            return new ArrayList<>(items.subList(0, safeLimit));
        }
        return items;
    }

    public List<ProjectStageDistributionVO> projectStageDistribution() {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Long scopePartnerId = currentUserService.requirePartnerScope(user);

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .isNull(Project::getDeletedAt);
        if (scopePartnerId != null) {
            wrapper.eq(Project::getPartnerId, scopePartnerId);
        }
        List<Project> projects = projectMapper.selectList(wrapper.select(Project::getStage));

        Map<String, Long> stageCount = projects.stream()
                .map(Project::getStage)
                .filter(stage -> stage != null && VALID_STAGES.contains(stage))
                .collect(Collectors.groupingBy(stage -> stage, Collectors.counting()));

        return stageCount.entrySet().stream()
                .map(entry -> {
                    ProjectStageDistributionVO vo = new ProjectStageDistributionVO();
                    vo.setStage(entry.getKey());
                    vo.setLabel(STAGE_LABELS.getOrDefault(entry.getKey(), entry.getKey()));
                    vo.setCount(entry.getValue());
                    return vo;
                })
                .sorted(Comparator.comparingLong(ProjectStageDistributionVO::getCount).reversed())
                .collect(Collectors.toList());
    }

    public List<ReportTrendVO> reportTrend(int days, String scope) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Long scopePartnerId = currentUserService.requirePartnerScope(user);

        int safeDays = (days < 1 || days > 90) ? 30 : days;
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(safeDays - 1L);

        Map<LocalDate, Long> dailyCount = new java.util.HashMap<>();
        if (!"diagnosis".equalsIgnoreCase(String.valueOf(scope))) {
            LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                    .ge(Report::getCreatedAt, startDate.atStartOfDay())
                    .le(Report::getCreatedAt, today.atTime(LocalTime.MAX))
                    .notIn(Report::getReportType, List.of("biweekly", "monthly", "quarterly"));
            if (scopePartnerId != null) {
                List<Long> projectIds = loadProjectIdsByPartner(scopePartnerId);
                if (projectIds.isEmpty()) {
                    wrapper.eq(Report::getId, -1L);
                } else {
                    wrapper.in(Report::getProjectId, projectIds);
                }
            }
            List<Report> reports = reportMapper.selectList(wrapper.select(Report::getCreatedAt));
            dailyCount.putAll(reports.stream()
                    .filter(report -> report.getCreatedAt() != null)
                    .collect(Collectors.groupingBy(report -> report.getCreatedAt().toLocalDate(), Collectors.counting())));
        }
        List<LocalDateTime> presaleGeneratedTimes = presaleReportVersionMapper.selectGeneratedCurrentReportTimes(
                startDate.atStartOfDay(),
                today.atTime(LocalTime.MAX),
                presaleCreatedByFilter(user)
        );
        presaleGeneratedTimes.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(LocalDateTime::toLocalDate, Collectors.counting()))
                .forEach((date, count) -> dailyCount.merge(date, count, Long::sum));

        List<ReportTrendVO> result = new ArrayList<>(safeDays);
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            ReportTrendVO vo = new ReportTrendVO();
            vo.setDate(date.toString());
            vo.setCount(dailyCount.getOrDefault(date, 0L));
            result.add(vo);
        }
        return result;
    }

    private Long countMonthlyPresaleReports(SysUser user, LocalDateTime monthStart) {
        Long count = presaleReportVersionMapper.countGeneratedCurrentReports(
                monthStart,
                LocalDateTime.now(),
                presaleCreatedByFilter(user)
        );
        return count == null ? 0L : count;
    }

    private Long presaleCreatedByFilter(SysUser user) {
        if (currentUserService.hasPermission("presale.report.manage")) {
            return null;
        }
        return user.getId();
    }

    private List<Long> loadProjectIdsByPartner(Long partnerId) {
        return projectMapper.selectList(
                        new LambdaQueryWrapper<Project>()
                                .isNull(Project::getDeletedAt)
                                .eq(Project::getPartnerId, partnerId)
                                .select(Project::getId)
                ).stream()
                .map(Project::getId)
                .collect(Collectors.toList());
    }
}
