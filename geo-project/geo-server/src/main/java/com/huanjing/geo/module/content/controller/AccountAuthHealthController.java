package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.AccountAuthHealthOverviewVO;
import com.huanjing.geo.module.content.service.AccountAuthHealthOverviewService;
import com.huanjing.geo.module.content.service.AccountAuthHealthScanCoordinator;
import com.huanjing.geo.module.content.service.SelfMediaRuntimeVersionCoverageService;
import com.huanjing.geo.module.content.dto.SelfMediaRuntimeVersionCoverageVO;
import com.huanjing.geo.module.content.service.ForumCookieHealthAlertService;
import com.huanjing.geo.module.content.service.SelfMediaAccountHealthAlertService;
import com.huanjing.geo.module.partner.service.PartnerFeatureAccessGuard;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/content/account-auth-health")
@RequiredArgsConstructor
public class AccountAuthHealthController {

    private final AccountAuthHealthOverviewService overviewService;
    private final SelfMediaAccountHealthAlertService selfMediaHealthAlertService;
    private final ForumCookieHealthAlertService forumCookieHealthAlertService;
    private final PartnerFeatureAccessGuard partnerFeatureAccessGuard;
    private final CurrentUserService currentUserService;
    private final AccountAuthHealthScanCoordinator scanCoordinator;
    private final SelfMediaRuntimeVersionCoverageService versionCoverageService;

    @GetMapping("/overview")
    public R<AccountAuthHealthOverviewVO> overview() {
        ensureAccess();
        requirePermission("self-media.auth-health.read", "content.read");
        return R.ok(overviewService.overview());
    }

    @PostMapping("/refresh")
    public R<AccountAuthHealthScanCoordinator.ScanLaunchResult> refresh() {
        ensureAccess();
        requirePermission("self-media.auth-health.recalculate", "company.update");
        return R.ok(scanCoordinator.launch());
    }

    @GetMapping("/runtime-version-coverage")
    public R<SelfMediaRuntimeVersionCoverageVO> runtimeVersionCoverage() {
        ensureAccess();
        return R.ok(versionCoverageService.coverage(7));
    }

    private void ensureAccess() {
        partnerFeatureAccessGuard.ensureInternalDeliveryFeature("account auth health operations");
    }

    private void requirePermission(String permission, String legacyPermission) {
        if (!currentUserService.hasPermission(permission)
                && !currentUserService.hasPermission(legacyPermission)) {
            throw new BizException(403, "No permission: " + permission);
        }
    }
}
