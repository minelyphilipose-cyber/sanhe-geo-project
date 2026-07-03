package com.huanjing.geo.module.extension.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.extension.dto.SelfMediaRuntimeEnvironmentVO;
import com.huanjing.geo.module.extension.service.SelfMediaRuntimeEnvironmentService;
import com.huanjing.geo.module.partner.service.PartnerFeatureAccessGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SelfMediaRuntimeEnvironment")
@RestController
@RequestMapping("/api/admin/self-media")
@RequiredArgsConstructor
public class SelfMediaRuntimeEnvironmentController {

    private final SelfMediaRuntimeEnvironmentService runtimeEnvironmentService;
    private final PartnerFeatureAccessGuard partnerFeatureAccessGuard;

    @GetMapping("/runtime-environments")
    public R<Page<SelfMediaRuntimeEnvironmentVO>> pageRuntimeEnvironments(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Boolean ready,
            @RequestParam(required = false) String blockedReason,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size) {
        partnerFeatureAccessGuard.ensureInternalDeliveryFeature("self-media runtime environment dashboard");
        return R.ok(runtimeEnvironmentService.pageRuntimeEnvironments(
                brandId,
                platform,
                ready,
                blockedReason,
                keyword,
                page == null ? 1 : page,
                size == null ? 20 : size
        ));
    }
}
