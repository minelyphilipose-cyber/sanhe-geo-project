package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.entity.WechatMenuConfig;
import com.huanjing.geo.module.content.service.WechatMenuConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/wechat/menu")
@RequiredArgsConstructor
public class WechatMenuConfigController {
    private final WechatMenuConfigService menuConfigService;

    @GetMapping("/accounts/{selfMediaAccountId}")
    public R<WechatMenuConfig> get(@PathVariable Long selfMediaAccountId) {
        return R.ok(menuConfigService.getMenuConfig(selfMediaAccountId));
    }

    @PostMapping("/accounts/{selfMediaAccountId}/initialize")
    public R<WechatMenuConfig> initialize(@PathVariable Long selfMediaAccountId) {
        return R.ok(menuConfigService.initializeMenu(selfMediaAccountId));
    }
}
