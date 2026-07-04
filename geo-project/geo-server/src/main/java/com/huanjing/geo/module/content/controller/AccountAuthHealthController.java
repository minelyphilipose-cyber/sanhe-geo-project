package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.AccountAuthHealthOverviewVO;
import com.huanjing.geo.module.content.service.AccountAuthHealthOverviewService;
import com.huanjing.geo.module.content.service.ForumCookieHealthAlertService;
import com.huanjing.geo.module.content.service.SelfMediaAccountHealthAlertService;
import com.huanjing.geo.module.partner.service.PartnerFeatureAccessGuard;
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

    @GetMapping("/overview")
    public R<AccountAuthHealthOverviewVO> overview() {
        ensureAccess();
        return R.ok(overviewService.overview());
    }

    @PostMapping("/refresh")
    public R<AccountAuthHealthOverviewVO> refresh() {
        ensureAccess();
        return R.ok(overviewService.refresh(selfMediaHealthAlertService, forumCookieHealthAlertService));
    }

    private void ensureAccess() {
        partnerFeatureAccessGuard.ensureInternalDeliveryFeature("account auth health operations");
    }
}
