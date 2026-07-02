package com.huanjing.geo.module.system.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.dto.DashboardOverviewVO;
import com.huanjing.geo.module.system.dto.PendingItemVO;
import com.huanjing.geo.module.system.dto.ProjectStageDistributionVO;
import com.huanjing.geo.module.system.dto.ReportTrendVO;
import com.huanjing.geo.module.system.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "工作台总览统计")
    @GetMapping("/overview")
    public R<DashboardOverviewVO> overview() {
        return R.ok(dashboardService.overview());
    }

    @Operation(summary = "待处理事项列表")
    @GetMapping("/pending-items")
    public R<List<PendingItemVO>> pendingItems(@RequestParam(defaultValue = "20") int limit) {
        return R.ok(dashboardService.pendingItems(limit));
    }

    @Operation(summary = "项目阶段分布")
    @GetMapping("/project-stage-distribution")
    public R<List<ProjectStageDistributionVO>> projectStageDistribution() {
        return R.ok(dashboardService.projectStageDistribution());
    }

    @Operation(summary = "报表生成趋势")
    @GetMapping("/report-trend")
    public R<List<ReportTrendVO>> reportTrend(@RequestParam(defaultValue = "30") int days,
                                              @RequestParam(required = false) String scope) {
        return R.ok(dashboardService.reportTrend(days, scope));
    }
}
