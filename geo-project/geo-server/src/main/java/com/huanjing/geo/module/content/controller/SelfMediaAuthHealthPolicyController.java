package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.SelfMediaAuthHealthPolicyUpdateRequest;
import com.huanjing.geo.module.content.dto.SelfMediaAuthHealthPolicyVO;
import com.huanjing.geo.module.content.dto.SelfMediaAuthHealthPolicyImpactVO;
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

    @GetMapping("/impact-preview")
    public R<SelfMediaAuthHealthPolicyImpactVO> impactPreview(@PathVariable String platform,
                                                              @RequestParam int reverifyIntervalDays,
                                                              @RequestParam int warningDays) {
        ensureAccess();
        if (reverifyIntervalDays < 1 || reverifyIntervalDays > 365) {
            throw new com.huanjing.geo.common.exception.BizException(400, "复验周期需在 1 至 365 天之间");
        }
        if (warningDays < 0 || warningDays > 90 || warningDays >= reverifyIntervalDays) {
            throw new com.huanjing.geo.common.exception.BizException(400, "预警天数需小于复验周期且不超过 90 天");
        }
        return R.ok(policyService.previewImpact(platform, reverifyIntervalDays, warningDays));
    }

    private void ensureAccess() {
        partnerFeatureAccessGuard.ensureInternalDeliveryFeature("self-media auth health policy");
    }
}
