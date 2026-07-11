package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.SelfMediaAccountManageRequest;
import com.huanjing.geo.module.content.dto.SelfMediaLoginVerificationVO;
import com.huanjing.geo.module.content.service.SelfMediaAccountPlatformEligibilityService;
import com.huanjing.geo.module.content.service.SelfMediaAccountService;
import com.huanjing.geo.module.content.service.SelfMediaLoginVerificationService;
import com.huanjing.geo.module.content.vo.DouyinAuthUrlVO;
import com.huanjing.geo.module.content.vo.DouyinCapabilityVO;
import com.huanjing.geo.module.content.vo.SelfMediaAccountPlatformOptionVO;
import com.huanjing.geo.module.content.vo.SelfMediaAccountVO;
import com.huanjing.geo.module.content.vo.WechatMpAuthUrlVO;
import com.huanjing.geo.module.content.vo.WechatMpCapabilityVO;
import com.huanjing.geo.module.content.douyin.DouyinAuthorizationService;
import com.huanjing.geo.module.content.wechat.WechatMpAuthorizationService;
import com.huanjing.geo.module.customer.service.BrandService;
import com.huanjing.geo.module.partner.service.PartnerFeatureAccessGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "SelfMediaAccount")
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class SelfMediaAccountController {
    private final SelfMediaAccountService selfMediaAccountService;
    private final BrandService brandService;
    private final SelfMediaAccountPlatformEligibilityService platformEligibilityService;
    private final WechatMpAuthorizationService authorizationService;
    private final DouyinAuthorizationService douyinAuthorizationService;
    private final PartnerFeatureAccessGuard partnerFeatureAccessGuard;
    private final SelfMediaLoginVerificationService loginVerificationService;

    @GetMapping("/self-media-accounts/wechat/capability")
    public R<WechatMpCapabilityVO> capability() {
        ensureInternalSelfMediaAccountAccess();
        return R.ok(selfMediaAccountService.capability());
    }

    @GetMapping("/self-media-accounts/douyin/capability")
    public R<DouyinCapabilityVO> douyinCapability() {
        ensureInternalSelfMediaAccountAccess();
        return R.ok(douyinAuthorizationService.capability());
    }

    @GetMapping("/self-media-accounts/wechat/auth-url")
    public R<WechatMpAuthUrlVO> authUrl(@RequestParam Long brandId,
                                        @RequestParam(required = false) Long redirectArticleId) {
        ensureInternalSelfMediaAccountAccess();
        brandService.requireBrandWithAccess(brandId, true);
        return R.ok(authorizationService.buildAuthUrl(brandId, redirectArticleId));
    }

    @GetMapping("/self-media-accounts/douyin/auth-url")
    public R<DouyinAuthUrlVO> douyinAuthUrl(@RequestParam Long brandId,
                                            @RequestParam(required = false) Long redirectArticleId) {
        ensureInternalSelfMediaAccountAccess();
        brandService.requireBrandWithAccess(brandId, true);
        return R.ok(douyinAuthorizationService.buildAuthUrl(brandId, redirectArticleId));
    }

    @GetMapping("/brands/{brandId}/self-media-accounts")
    public R<List<SelfMediaAccountVO>> listByBrand(@PathVariable Long brandId) {
        ensureInternalSelfMediaAccountAccess();
        brandService.requireBrandWithAccess(brandId, false);
        return R.ok(selfMediaAccountService.listByBrand(brandId));
    }

    @GetMapping("/brands/{brandId}/self-media-account-platforms")
    public R<List<SelfMediaAccountPlatformOptionVO>> listAvailablePlatforms(@PathVariable Long brandId) {
        ensureInternalSelfMediaAccountAccess();
        brandService.requireBrandWithAccess(brandId, false);
        return R.ok(platformEligibilityService.listByBrand(brandId));
    }

    @PostMapping("/brands/{brandId}/self-media-accounts")
    public R<SelfMediaAccountVO> create(@PathVariable Long brandId,
                                        @Valid @RequestBody SelfMediaAccountManageRequest request) {
        ensureInternalSelfMediaAccountAccess();
        return R.ok(selfMediaAccountService.createCookieAccount(brandId, request));
    }

    @PutMapping("/self-media-accounts/{id}")
    public R<SelfMediaAccountVO> update(@PathVariable Long id,
                                        @Valid @RequestBody SelfMediaAccountManageRequest request) {
        ensureInternalSelfMediaAccountAccess();
        return R.ok(selfMediaAccountService.updateCookieAccount(id, request));
    }

    @PostMapping("/self-media-accounts/{id}/check-auth")
    public R<SelfMediaAccountVO> checkAuth(@PathVariable Long id) {
        ensureInternalSelfMediaAccountAccess();
        return R.ok(selfMediaAccountService.checkAuth(id));
    }

    @PostMapping("/brands/{brandId}/self-media-accounts/{id}/login-verifications")
    public R<SelfMediaLoginVerificationVO> createLoginVerification(@PathVariable Long brandId,
                                                                   @PathVariable Long id) {
        ensureInternalSelfMediaAccountAccess();
        return R.ok(loginVerificationService.create(brandId, id));
    }

    @GetMapping("/brands/{brandId}/self-media-accounts/{id}/login-verifications/{verificationId}")
    public R<SelfMediaLoginVerificationVO> getLoginVerification(@PathVariable Long brandId,
                                                                @PathVariable Long id,
                                                                @PathVariable Long verificationId) {
        ensureInternalSelfMediaAccountAccess();
        return R.ok(loginVerificationService.get(brandId, id, verificationId));
    }

    @DeleteMapping("/self-media-accounts/{id}/cookie-credential")
    public R<SelfMediaAccountVO> destroyCookieCredential(@PathVariable Long id) {
        ensureInternalSelfMediaAccountAccess();
        return R.ok(selfMediaAccountService.destroyCookieCredential(id));
    }

    @DeleteMapping("/self-media-accounts/{id}")
    public R<Void> deleteAccount(@PathVariable Long id) {
        ensureInternalSelfMediaAccountAccess();
        selfMediaAccountService.deleteAccount(id);
        return R.ok();
    }

    private void ensureInternalSelfMediaAccountAccess() {
        partnerFeatureAccessGuard.ensureInternalDeliveryFeature("self-media account operations");
    }
}
