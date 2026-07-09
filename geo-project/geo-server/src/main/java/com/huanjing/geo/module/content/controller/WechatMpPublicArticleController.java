package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.WechatMpArticleListVO;
import com.huanjing.geo.module.content.service.WechatMpPublicArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/wechat/mp")
@RequiredArgsConstructor
public class WechatMpPublicArticleController {
    private final WechatMpPublicArticleService articleService;

    @GetMapping("/{publicSlug}/articles")
    public R<WechatMpArticleListVO> list(@PathVariable String publicSlug,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return R.ok(articleService.list(publicSlug, page, size));
    }
}
