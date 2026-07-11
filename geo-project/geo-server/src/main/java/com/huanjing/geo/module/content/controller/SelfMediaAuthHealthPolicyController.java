package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.SelfMediaAuthHealthPolicyUpdateRequest;
import com.huanjing.geo.module.content.dto.SelfMediaAuthHealthPolicyVO;
import com.huanjing.geo.module.content.entity.SelfMediaAuthHealthPolicyAudit;
import com.huanjing.geo.module.content.service.SelfMediaAuthHealthPolicyService;
import com.huanjing.geo.module.partner.service.PartnerFeatureAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/content/self-media-platforms/{platform}/auth-health-policy")
@RequiredArgsConstructor
public class SelfMediaAuthHealthPolicyController {
    private final SelfMediaAuthHealthPolicyService policyService;
    private final PartnerFeatureAccessGuard partnerFeatureAccessGuard;

    @GetMapping
    public R<SelfMediaAuthHealthPolicyVO> get(@PathVariable String platform) {
        ensureAccess();
        return R.ok(policyService.get(platform));
    }

    @PutMapping
    public R<SelfMediaAuthHealthPolicyVO> update(@PathVariable String platform,
                                                 @Valid @RequestBody SelfMediaAuthHealthPolicyUpdateRequest request) {
        ensureAccess();
        return R.ok(policyService.update(platform, request));
    }

    @GetMapping("/audits")
    public R<List<SelfMediaAuthHealthPolicyAudit>> audits(@PathVariable String platform) {
        ensureAccess();
        return R.ok(policyService.audits(platform));
    }

    private void ensureAccess() {
        partnerFeatureAccessGuard.ensureInternalDeliveryFeature("self-media auth health policy");
    }
}
