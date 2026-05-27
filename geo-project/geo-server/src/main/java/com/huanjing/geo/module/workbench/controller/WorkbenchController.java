package com.huanjing.geo.module.workbench.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.workbench.dto.ManagerWorkbenchOverviewVO;
import com.huanjing.geo.module.workbench.dto.OperatorWorkbenchOverviewVO;
import com.huanjing.geo.module.workbench.dto.SalesWorkbenchOverviewVO;
import com.huanjing.geo.module.workbench.dto.SuperAdminWorkbenchOverviewVO;
import com.huanjing.geo.module.workbench.service.WorkbenchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Role Workbench")
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    @GetMapping("/operator/overview")
    public R<OperatorWorkbenchOverviewVO> operatorOverview() {
        return R.ok(workbenchService.operatorOverview());
    }

    @GetMapping("/sales/overview")
    public R<SalesWorkbenchOverviewVO> salesOverview() {
        return R.ok(workbenchService.salesOverview());
    }

    @GetMapping("/manager/overview")
    public R<ManagerWorkbenchOverviewVO> managerOverview() {
        return R.ok(workbenchService.managerOverview());
    }

    @GetMapping("/super-admin/overview")
    public R<SuperAdminWorkbenchOverviewVO> superAdminOverview() {
        return R.ok(workbenchService.superAdminOverview());
    }
}
