package com.huanjing.geo.module.project.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.service.PackagePlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "PackagePlan")
@RestController
@RequestMapping("/api/package-plans")
@RequiredArgsConstructor
public class PackagePlanController {

    private final PackagePlanService packagePlanService;

    @GetMapping("/enabled")
    public R<List<PackagePlan>> listEnabled() {
        return R.ok(packagePlanService.listEnabled());
    }
}
