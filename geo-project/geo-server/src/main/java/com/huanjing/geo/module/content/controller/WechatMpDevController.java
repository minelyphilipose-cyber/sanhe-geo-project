package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.WechatMpDevSeedRequest;
import com.huanjing.geo.module.content.service.SelfMediaAccountService;
import com.huanjing.geo.module.content.vo.SelfMediaAccountVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/content/dev/wechat-mp")
@RequiredArgsConstructor
public class WechatMpDevController {
    private final SelfMediaAccountService selfMediaAccountService;

    @PostMapping("/seed")
    public R<SelfMediaAccountVO> seed(@Valid @RequestBody WechatMpDevSeedRequest request) {
        return R.ok(selfMediaAccountService.seedForDev(request));
    }
}
