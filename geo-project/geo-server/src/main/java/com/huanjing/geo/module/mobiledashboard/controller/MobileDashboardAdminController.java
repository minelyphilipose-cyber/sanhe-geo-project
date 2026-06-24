package com.huanjing.geo.module.mobiledashboard.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.mobiledashboard.dto.EntityJudgeBudgetConfigRequest;
import com.huanjing.geo.module.mobiledashboard.dto.EntityJudgeBudgetConfigVO;
import com.huanjing.geo.module.mobiledashboard.dto.EntityJudgeRunRequest;
import com.huanjing.geo.module.mobiledashboard.dto.EntityJudgeRunVO;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardOperationsVO;
import com.huanjing.geo.module.mobiledashboard.dto.ProjectCompetitorConfigRequest;
import com.huanjing.geo.module.mobiledashboard.dto.ProjectCompetitorConfigVO;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardOpsService;
import com.huanjing.geo.module.mobiledashboard.service.MobileEntityJudgeBudgetService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardEntityJudgeService;
import com.huanjing.geo.module.mobiledashboard.service.ProjectCompetitorConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/projects/{projectId:\\d+}/mobile-dashboard")
@RequiredArgsConstructor
public class MobileDashboardAdminController {
    private final ProjectCompetitorConfigService competitorConfigService;
    private final MobileDashboardEntityJudgeService entityJudgeService;
    private final MobileDashboardOpsService opsService;
    private final MobileEntityJudgeBudgetService budgetService;

    @GetMapping("/competitors")
    public R<List<ProjectCompetitorConfigVO>> competitors(@PathVariable Long projectId) {
        return R.ok(competitorConfigService.list(projectId));
    }

    @PutMapping("/competitors")
    public R<List<ProjectCompetitorConfigVO>> replaceCompetitors(@PathVariable Long projectId,
                                                                 @Valid @RequestBody ProjectCompetitorConfigRequest request) {
        return R.ok(competitorConfigService.replace(projectId, request));
    }

    @PostMapping("/entity-judge/run")
    public R<EntityJudgeRunVO> runEntityJudge(@PathVariable Long projectId,
                                              @RequestBody(required = false) EntityJudgeRunRequest request) {
        EntityJudgeRunRequest safeRequest = request == null ? new EntityJudgeRunRequest() : request;
        safeRequest.setProjectId(projectId);
        return R.ok(entityJudgeService.runOnce(safeRequest));
    }

    @GetMapping("/operations")
    public R<MobileDashboardOperationsVO> operations(@PathVariable Long projectId,
                                                     @RequestParam(required = false) LocalDate startDate,
                                                     @RequestParam(required = false) LocalDate endDate) {
        return R.ok(opsService.operations(projectId, startDate, endDate));
    }

    @GetMapping("/entity-judge/budget")
    public R<EntityJudgeBudgetConfigVO> getBudget(@PathVariable Long projectId) {
        return R.ok(budgetService.getProjectConfig(projectId));
    }

    @PutMapping("/entity-judge/budget")
    public R<EntityJudgeBudgetConfigVO> updateBudget(@PathVariable Long projectId,
                                                     @RequestBody(required = false) EntityJudgeBudgetConfigRequest request) {
        return R.ok(budgetService.updateProjectConfig(projectId, request));
    }
}
