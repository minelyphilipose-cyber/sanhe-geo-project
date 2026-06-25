package com.huanjing.geo.module.mobiledashboard.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardShareCreateRequest;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardShareAccessSummaryVO;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardShareVO;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardOpsService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardShareService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "MobileDashboardManage")
@RestController
@RequiredArgsConstructor
public class MobileDashboardManageController {

    private final MobileDashboardShareService mobileDashboardShareService;
    private final MobileDashboardOpsService mobileDashboardOpsService;

    @GetMapping("/api/projects/{projectId}/mobile-dashboard-share")
    public R<List<MobileDashboardShareVO>> listShares(@PathVariable Long projectId) {
        return R.ok(mobileDashboardShareService.listShares(projectId));
    }

    @GetMapping("/api/projects/{projectId}/mobile-dashboard-share/access-summary")
    public R<List<MobileDashboardShareAccessSummaryVO>> shareAccessSummary(@PathVariable Long projectId) {
        return R.ok(mobileDashboardOpsService.shareAccessSummary(projectId));
    }

    @PostMapping("/api/projects/{projectId}/mobile-dashboard-share")
    public R<MobileDashboardShareVO> createShare(@PathVariable Long projectId,
                                                 @RequestBody(required = false) MobileDashboardShareCreateRequest request) {
        return R.ok(mobileDashboardShareService.createShare(projectId, request));
    }

    @PutMapping("/api/mobile-dashboard-shares/{id}/disable")
    public R<Void> disableShare(@PathVariable Long id) {
        mobileDashboardShareService.disableShare(id);
        return R.ok();
    }

    @DeleteMapping("/api/mobile-dashboard-shares/{id}")
    public R<Void> deleteShare(@PathVariable Long id) {
        mobileDashboardShareService.deleteShare(id);
        return R.ok();
    }
}
