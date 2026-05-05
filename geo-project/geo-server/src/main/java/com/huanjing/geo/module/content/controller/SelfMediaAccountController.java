package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.service.SelfMediaAccountService;
import com.huanjing.geo.module.content.vo.DouyinAuthUrlVO;
import com.huanjing.geo.module.content.vo.DouyinCapabilityVO;
import com.huanjing.geo.module.content.vo.SelfMediaAccountVO;
import com.huanjing.geo.module.content.vo.WechatMpAuthUrlVO;
import com.huanjing.geo.module.content.vo.WechatMpCapabilityVO;
import com.huanjing.geo.module.content.douyin.DouyinAuthorizationService;
import com.huanjing.geo.module.content.wechat.WechatMpAuthorizationService;
import com.huanjing.geo.module.customer.service.BrandService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private final WechatMpAuthorizationService authorizationService;
    private final DouyinAuthorizationService douyinAuthorizationService;

    @GetMapping("/self-media-accounts/wechat/capability")
    public R<WechatMpCapabilityVO> capability() {
        return R.ok(selfMediaAccountService.capability());
    }

    @GetMapping("/self-media-accounts/douyin/capability")
    public R<DouyinCapabilityVO> douyinCapability() {
        return R.ok(douyinAuthorizationService.capability());
    }

    @GetMapping("/self-media-accounts/wechat/auth-url")
    public R<WechatMpAuthUrlVO> authUrl(@RequestParam Long brandId,
                                        @RequestParam(required = false) Long redirectArticleId) {
        brandService.requireBrandWithAccess(brandId, true);
        return R.ok(authorizationService.buildAuthUrl(brandId, redirectArticleId));
    }

    @GetMapping("/self-media-accounts/douyin/auth-url")
    public R<DouyinAuthUrlVO> douyinAuthUrl(@RequestParam Long brandId,
                                            @RequestParam(required = false) Long redirectArticleId) {
        brandService.requireBrandWithAccess(brandId, true);
        return R.ok(douyinAuthorizationService.buildAuthUrl(brandId, redirectArticleId));
    }

    @GetMapping("/brands/{brandId}/self-media-accounts")
    public R<List<SelfMediaAccountVO>> listByBrand(@PathVariable Long brandId) {
        brandService.requireBrandWithAccess(brandId, true);
        return R.ok(selfMediaAccountService.listByBrand(brandId));
    }

    @PostMapping("/self-media-accounts/{id}/check-auth")
    public R<SelfMediaAccountVO> checkAuth(@PathVariable Long id) {
        return R.ok(selfMediaAccountService.checkAuth(id));
    }
}
