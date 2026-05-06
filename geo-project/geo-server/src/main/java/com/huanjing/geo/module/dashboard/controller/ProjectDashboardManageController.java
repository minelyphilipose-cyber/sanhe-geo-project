package com.huanjing.geo.module.dashboard.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.dashboard.dto.ProjectDashboardAdviceRequest;
import com.huanjing.geo.module.dashboard.dto.ProjectDashboardAdviceVO;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardShare;
import com.huanjing.geo.module.dashboard.service.ProjectDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "ProjectDashboardManage")
@RestController
@RequiredArgsConstructor
public class ProjectDashboardManageController {

    private final ProjectDashboardService projectDashboardService;

    @GetMapping("/api/projects/{projectId}/dashboard-share")
    public R<List<ProjectDashboardShare>> listShares(@PathVariable Long projectId) {
        return R.ok(projectDashboardService.listShares(projectId));
    }

    @PostMapping("/api/projects/{projectId}/dashboard-share")
    public R<ProjectDashboardShare> createShare(@PathVariable Long projectId) {
        return R.ok(projectDashboardService.createShare(projectId));
    }

    @PostMapping("/api/projects/{projectId}/dashboard-snapshot/refresh")
    public R<Map<String, Object>> refreshSnapshot(@PathVariable Long projectId) {
        return R.ok(projectDashboardService.refreshSnapshot(projectId));
    }

    @GetMapping("/api/projects/{projectId}/dashboard-snapshot/status")
    public R<Map<String, Object>> getSnapshotStatus(@PathVariable Long projectId) {
        return R.ok(projectDashboardService.getSnapshotStatus(projectId));
    }

    @GetMapping("/api/projects/{projectId}/dashboard-advice")
    public R<ProjectDashboardAdviceVO> getAdvice(@PathVariable Long projectId) {
        return R.ok(projectDashboardService.getAdvice(projectId));
    }

    @PutMapping("/api/projects/{projectId}/dashboard-advice")
    public R<ProjectDashboardAdviceVO> saveAdvice(@PathVariable Long projectId,
                                                  @RequestBody ProjectDashboardAdviceRequest req) {
        return R.ok(projectDashboardService.saveAdvice(projectId, req));
    }

    @PostMapping("/api/projects/{projectId}/dashboard-advice/publish")
    public R<ProjectDashboardAdviceVO> publishAdvice(@PathVariable Long projectId,
                                                     @RequestBody ProjectDashboardAdviceRequest req) {
        return R.ok(projectDashboardService.publishAdvice(projectId, req));
    }

    @PutMapping("/api/dashboard-shares/{id}/disable")
    public R<Void> disableShare(@PathVariable Long id) {
        projectDashboardService.disableShare(id);
        return R.ok();
    }
}
