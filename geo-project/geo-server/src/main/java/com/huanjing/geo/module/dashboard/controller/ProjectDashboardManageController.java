package com.huanjing.geo.module.dashboard.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardShare;
import com.huanjing.geo.module.dashboard.service.ProjectDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PutMapping("/api/dashboard-shares/{id}/disable")
    public R<Void> disableShare(@PathVariable Long id) {
        projectDashboardService.disableShare(id);
        return R.ok();
    }
}
