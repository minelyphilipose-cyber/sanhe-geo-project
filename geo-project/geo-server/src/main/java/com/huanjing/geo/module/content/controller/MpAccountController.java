package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.service.MpAccountService;
import com.huanjing.geo.module.content.vo.MpAccountVO;
import com.huanjing.geo.module.content.vo.WechatMpAuthUrlVO;
import com.huanjing.geo.module.content.vo.WechatMpCapabilityVO;
import com.huanjing.geo.module.content.wechat.WechatMpAuthorizationService;
import com.huanjing.geo.module.customer.service.BrandService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "MpAccount")
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class MpAccountController {
    private final MpAccountService mpAccountService;
    private final BrandService brandService;
    private final WechatMpAuthorizationService authorizationService;

    @GetMapping("/mp-accounts/wechat/capability")
    public R<WechatMpCapabilityVO> capability() {
        return R.ok(mpAccountService.capability());
    }

    @GetMapping("/mp-accounts/wechat/auth-url")
    public R<WechatMpAuthUrlVO> authUrl(@RequestParam Long brandId,
                                        @RequestParam(required = false) Long redirectArticleId) {
        brandService.requireBrandWithAccess(brandId, true);
        return R.ok(authorizationService.buildAuthUrl(brandId, redirectArticleId));
    }

    @GetMapping("/brands/{brandId}/mp-accounts")
    public R<List<MpAccountVO>> listByBrand(@PathVariable Long brandId) {
        brandService.requireBrandWithAccess(brandId, true);
        return R.ok(mpAccountService.listByBrand(brandId));
    }

    @PostMapping("/mp-accounts/{id}/check-auth")
    public R<MpAccountVO> checkAuth(@PathVariable Long id) {
        return R.ok(mpAccountService.checkAuth(id));
    }
}
