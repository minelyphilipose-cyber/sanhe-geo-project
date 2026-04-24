package com.huanjing.geo.module.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.dto.AiPlatformConfigCreateRequest;
import com.huanjing.geo.module.system.dto.AiPlatformPresaleEnabledUpdateRequest;
import com.huanjing.geo.module.system.dto.AiPlatformConfigUpdateRequest;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.service.AiPlatformConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AiPlatformConfigAdmin")
@RestController
@RequestMapping("/api/admin/platform-configs")
@RequiredArgsConstructor
public class AiPlatformConfigAdminController {

    private final AiPlatformConfigService aiPlatformConfigService;

    @GetMapping
    public R<Page<AiPlatformConfig>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String priorityLevel,
            @RequestParam(required = false) Boolean enabled
    ) {
        return R.ok(aiPlatformConfigService.page(current, size, keyword, priorityLevel, enabled));
    }

    @PostMapping
    public R<AiPlatformConfig> create(@Valid @RequestBody AiPlatformConfigCreateRequest req) {
        return R.ok(aiPlatformConfigService.create(req));
    }

    @PutMapping("/{id}")
    public R<AiPlatformConfig> update(@PathVariable Long id, @Valid @RequestBody AiPlatformConfigUpdateRequest req) {
        return R.ok(aiPlatformConfigService.update(id, req));
    }

    @RequestMapping(path = "/{id}/presale-enabled", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public R<AiPlatformConfig> updatePresaleEnabled(@PathVariable Long id,
                                                    @Valid @RequestBody AiPlatformPresaleEnabledUpdateRequest req) {
        return R.ok(aiPlatformConfigService.updatePresaleEnabled(id, req.getEnabledForPresale()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        aiPlatformConfigService.delete(id);
        return R.ok();
    }
}
