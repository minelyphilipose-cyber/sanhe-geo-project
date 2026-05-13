package com.huanjing.geo.module.presale.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.presale.dto.request.PresalePage03MarketConfigUpdateRequest;
import com.huanjing.geo.module.presale.persist.entity.PresalePage03MarketConfig;
import com.huanjing.geo.module.presale.service.PresalePage03MarketConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presale/page03-market-config")
@RequiredArgsConstructor
public class PresalePage03MarketConfigController {

    private final PresalePage03MarketConfigService service;

    @GetMapping
    public R<PresalePage03MarketConfig> get() {
        return R.ok(service.getConfigForAdmin());
    }

    @PutMapping
    public R<PresalePage03MarketConfig> update(@Valid @RequestBody PresalePage03MarketConfigUpdateRequest req) {
        return R.ok(service.update(req));
    }
}
