package com.huanjing.geo.module.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.entity.PackageContentConfig;
import com.huanjing.geo.module.project.dto.PackageContentConfigRequest;
import com.huanjing.geo.module.project.dto.PackagePlanCreateRequest;
import com.huanjing.geo.module.project.dto.PackagePlanStatusUpdateRequest;
import com.huanjing.geo.module.project.dto.PackagePlanUpdateRequest;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.service.PackagePlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "PackagePlanAdmin")
@RestController
@RequestMapping("/api/admin/package-plans")
@RequiredArgsConstructor
public class PackagePlanAdminController {

    private final PackagePlanService packagePlanService;

    @GetMapping
    public R<Page<PackagePlan>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled
    ) {
        return R.ok(packagePlanService.page(current, size, keyword, enabled));
    }

    @PostMapping
    public R<PackagePlan> create(@Valid @RequestBody PackagePlanCreateRequest req) {
        return R.ok(packagePlanService.create(req));
    }

    @PutMapping("/{id}")
    public R<PackagePlan> update(@PathVariable Long id, @Valid @RequestBody PackagePlanUpdateRequest req) {
        return R.ok(packagePlanService.update(id, req));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody PackagePlanStatusUpdateRequest req) {
        packagePlanService.updateStatus(id, req.getEnabled());
        return R.ok();
    }

    @GetMapping("/{id}/content-configs")
    public R<List<PackageContentConfig>> contentConfigs(@PathVariable Long id) {
        return R.ok(packagePlanService.listContentConfigs(id));
    }

    @PutMapping("/{id}/content-configs")
    public R<List<PackageContentConfig>> updateContentConfigs(@PathVariable Long id,
                                                              @RequestBody(required = false) List<PackageContentConfigRequest> req) {
        return R.ok(packagePlanService.saveContentConfigsByPlanId(id, req));
    }
}
