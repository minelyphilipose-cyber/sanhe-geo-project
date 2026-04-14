package com.huanjing.geo.module.system.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.dto.PublishSiteCreateRequest;
import com.huanjing.geo.module.system.dto.PublishSiteStatusUpdateRequest;
import com.huanjing.geo.module.system.dto.PublishSiteUpdateRequest;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.service.PublishSiteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "PublishSiteAdmin")
@RestController
@RequestMapping("/api/system/publish-sites")
@RequiredArgsConstructor
public class PublishSiteAdminController {

    private final PublishSiteService publishSiteService;

    @GetMapping
    public R<List<PublishSite>> list(@RequestParam(required = false) String tier,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String industry) {
        return R.ok(publishSiteService.list(tier, status, industry));
    }

    @GetMapping("/{id}")
    public R<PublishSite> detail(@PathVariable Long id) {
        return R.ok(publishSiteService.detail(id));
    }

    @PostMapping
    public R<PublishSite> create(@Valid @RequestBody PublishSiteCreateRequest req) {
        return R.ok(publishSiteService.create(req));
    }

    @PutMapping("/{id}")
    public R<PublishSite> update(@PathVariable Long id, @Valid @RequestBody PublishSiteUpdateRequest req) {
        return R.ok(publishSiteService.update(id, req));
    }

    @PatchMapping("/{id}/status")
    public R<PublishSite> updateStatus(@PathVariable Long id, @Valid @RequestBody PublishSiteStatusUpdateRequest req) {
        return R.ok(publishSiteService.updateStatus(id, req));
    }

    @PostMapping("/{id}/test")
    public R<Map<String, Object>> test(@PathVariable Long id) {
        return R.ok(publishSiteService.testConnectivity(id));
    }
}
