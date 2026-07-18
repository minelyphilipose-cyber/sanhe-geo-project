package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentAccountCreateRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentAccountUpdateRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentCreateRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentLoginStatusRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentUpdateRequest;
import com.huanjing.geo.module.content.service.BrowserEnvironmentService;
import com.huanjing.geo.module.content.vo.BrowserEnvironmentAccountVO;
import com.huanjing.geo.module.content.vo.BrowserEnvironmentVO;
import com.huanjing.geo.module.content.vo.SelfMediaAutomationReadinessVO;
import com.huanjing.geo.module.partner.service.PartnerFeatureAccessGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "BrowserEnvironment")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BrowserEnvironmentController {
    private final BrowserEnvironmentService browserEnvironmentService;
    private final PartnerFeatureAccessGuard partnerFeatureAccessGuard;

    @GetMapping("/browser-environments")
    public R<List<BrowserEnvironmentVO>> listEnvironments(@RequestParam Long brandId) {
        ensureInternalBrowserEnvironmentAccess();
        return R.ok(browserEnvironmentService.listEnvironments(brandId));
    }

    @GetMapping("/brands/{brandId}/self-media-automation/readiness")
    public R<SelfMediaAutomationReadinessVO> selfMediaAutomationReadiness(@PathVariable Long brandId) {
        ensureInternalBrowserEnvironmentAccess();
        return R.ok(browserEnvironmentService.selfMediaAutomationReadiness(brandId));
    }

    @PostMapping("/browser-environments")
    public R<BrowserEnvironmentVO> createEnvironment(@Valid @RequestBody BrowserEnvironmentCreateRequest request) {
        ensureInternalBrowserEnvironmentAccess();
        return R.ok(browserEnvironmentService.createEnvironment(request));
    }

    @PatchMapping("/browser-environments/{id}")
    public R<BrowserEnvironmentVO> updateEnvironment(@PathVariable Long id,
                                                     @RequestBody BrowserEnvironmentUpdateRequest request) {
        ensureInternalBrowserEnvironmentAccess();
        return R.ok(browserEnvironmentService.updateEnvironment(id, request));
    }

    @DeleteMapping("/browser-environments/{id}")
    public R<Void> deleteEnvironment(@PathVariable Long id) {
        ensureInternalBrowserEnvironmentAccess();
        browserEnvironmentService.deleteEnvironment(id);
        return R.ok();
    }

    @GetMapping("/browser-environment-accounts")
    public R<List<BrowserEnvironmentAccountVO>> listEnvironmentAccounts(@RequestParam Long brandId,
                                                                        @RequestParam(required = false) String platform) {
        ensureInternalBrowserEnvironmentAccess();
        return R.ok(browserEnvironmentService.listEnvironmentAccounts(brandId, platform));
    }

    @PostMapping("/browser-environment-accounts")
    public R<BrowserEnvironmentAccountVO> createEnvironmentAccount(
            @Valid @RequestBody BrowserEnvironmentAccountCreateRequest request) {
        ensureInternalBrowserEnvironmentAccess();
        return R.ok(browserEnvironmentService.createEnvironmentAccount(request));
    }

    @PatchMapping("/browser-environment-accounts/{id}")
    public R<BrowserEnvironmentAccountVO> updateEnvironmentAccount(
            @PathVariable Long id,
            @RequestBody BrowserEnvironmentAccountUpdateRequest request) {
        ensureInternalBrowserEnvironmentAccess();
        return R.ok(browserEnvironmentService.updateEnvironmentAccount(id, request));
    }

    @DeleteMapping("/browser-environment-accounts/{id}")
    public R<Void> deleteEnvironmentAccount(@PathVariable Long id) {
        ensureInternalBrowserEnvironmentAccess();
        browserEnvironmentService.deleteEnvironmentAccount(id);
        return R.ok();
    }

    @GetMapping("/browser-environment-accounts/by-self-media/{selfMediaAccountId}/status")
    public R<BrowserEnvironmentAccountVO> getBySelfMediaAccount(@PathVariable Long selfMediaAccountId) {
        ensureInternalBrowserEnvironmentAccess();
        return R.ok(browserEnvironmentService.getBySelfMediaAccount(selfMediaAccountId));
    }

    @PostMapping("/browser-environment-accounts/{id}/login-status")
    public R<BrowserEnvironmentAccountVO> reportLoginStatus(
            @PathVariable Long id,
            @Valid @RequestBody BrowserEnvironmentLoginStatusRequest request) {
        ensureInternalBrowserEnvironmentAccess();
        return R.ok(browserEnvironmentService.reportLoginStatus(id, request));
    }

    @PostMapping("/browser-environment-accounts/{id}/mark-login-expired")
    public R<BrowserEnvironmentAccountVO> markLoginExpired(@PathVariable Long id) {
        ensureInternalBrowserEnvironmentAccess();
        return R.ok(browserEnvironmentService.markLoginExpired(id));
    }

    @PostMapping("/browser-environment-accounts/{id}/reset-login-identity")
    public R<BrowserEnvironmentAccountVO> resetLoginIdentity(@PathVariable Long id) {
        ensureInternalBrowserEnvironmentAccess();
        return R.ok(browserEnvironmentService.resetLoginIdentity(id));
    }

    private void ensureInternalBrowserEnvironmentAccess() {
        partnerFeatureAccessGuard.ensureInternalDeliveryFeature("browser environment operations");
    }
}
