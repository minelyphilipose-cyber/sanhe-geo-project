package com.huanjing.geo.module.project.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.partner.service.PartnerResponseSanitizer;
import com.huanjing.geo.module.project.service.PackagePlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PackagePlan")
@RestController
@RequestMapping("/api/package-plans")
@RequiredArgsConstructor
public class PackagePlanController {

    private final PackagePlanService packagePlanService;
    private final PartnerResponseSanitizer partnerResponseSanitizer;

    @GetMapping("/enabled")
    public R<?> listEnabled() {
        return R.ok(partnerResponseSanitizer.packagePlans(packagePlanService.listEnabled()));
    }
}
