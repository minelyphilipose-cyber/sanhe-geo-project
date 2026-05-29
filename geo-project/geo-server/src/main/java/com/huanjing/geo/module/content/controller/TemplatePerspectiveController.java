package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.TemplatePerspectiveDtos.BrandChannelPerspectiveSaveRequest;
import com.huanjing.geo.module.content.dto.TemplatePerspectiveDtos.BrandChannelPerspectiveVO;
import com.huanjing.geo.module.content.dto.TemplatePerspectiveDtos.ConfigListResponse;
import com.huanjing.geo.module.content.dto.TemplatePerspectiveDtos.PerspectiveStatusRequest;
import com.huanjing.geo.module.content.dto.TemplatePerspectiveDtos.PerspectiveVO;
import com.huanjing.geo.module.content.dto.TemplatePerspectiveDtos.ResolveResponse;
import com.huanjing.geo.module.content.service.TemplatePerspectiveService;
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

@RestController
@RequestMapping("/api/content/template-perspectives")
@RequiredArgsConstructor
public class TemplatePerspectiveController {

    private final TemplatePerspectiveService perspectiveService;

    @GetMapping
    public R<List<PerspectiveVO>> perspectives(@RequestParam(defaultValue = "false") boolean includeDisabled) {
        return R.ok(perspectiveService.perspectives(includeDisabled));
    }

    @PatchMapping("/{code}/enabled")
    public R<PerspectiveVO> updateEnabled(@PathVariable String code,
                                          @Valid @RequestBody PerspectiveStatusRequest req) {
        return R.ok(perspectiveService.updatePerspectiveEnabled(code, req.enabled()));
    }

    @GetMapping("/brand-configs")
    public R<ConfigListResponse> brandConfigs(@RequestParam Long brandId) {
        return R.ok(perspectiveService.brandConfigs(brandId));
    }

    @PostMapping("/brand-configs")
    public R<BrandChannelPerspectiveVO> saveBrandConfig(@Valid @RequestBody BrandChannelPerspectiveSaveRequest req) {
        return R.ok(perspectiveService.saveBrandConfig(req));
    }

    @DeleteMapping("/brand-configs/{id}")
    public R<Void> deleteBrandConfig(@PathVariable Long id) {
        perspectiveService.deleteBrandConfig(id);
        return R.ok();
    }

    @GetMapping("/resolve")
    public R<ResolveResponse> resolve(@RequestParam Long brandId,
                                      @RequestParam String channelGroupCode,
                                      @RequestParam(required = false) String channelSubCode) {
        return R.ok(perspectiveService.resolvePreview(brandId, channelGroupCode, channelSubCode));
    }
}
