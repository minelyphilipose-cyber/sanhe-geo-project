package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.MpAccountDevSeedRequest;
import com.huanjing.geo.module.content.service.MpAccountService;
import com.huanjing.geo.module.content.vo.MpAccountVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/content/dev/mp-accounts")
@RequiredArgsConstructor
public class MpAccountDevController {
    private final MpAccountService mpAccountService;

    @PostMapping("/seed")
    public R<MpAccountVO> seed(@Valid @RequestBody MpAccountDevSeedRequest request) {
        return R.ok(mpAccountService.seedForDev(request));
    }
}
