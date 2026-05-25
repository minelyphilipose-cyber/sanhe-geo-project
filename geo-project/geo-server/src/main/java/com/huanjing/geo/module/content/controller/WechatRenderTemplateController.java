package com.huanjing.geo.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateParseRequest;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateParseResponse;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateSaveRequest;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateUpdateRequest;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateVersionSaveRequest;
import com.huanjing.geo.module.content.entity.PlatformRenderTemplate;
import com.huanjing.geo.module.content.entity.PlatformRenderTemplateVersion;
import com.huanjing.geo.module.content.service.render.wechat.WechatRenderTemplateService;
import com.huanjing.geo.module.content.service.render.wechat.WechatTemplateImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wechat-render-templates")
@RequiredArgsConstructor
public class WechatRenderTemplateController {
    private final WechatTemplateImportService importService;
    private final WechatRenderTemplateService templateService;

    @PostMapping("/parse")
    public R<TemplateParseResponse> parse(@Valid @RequestBody TemplateParseRequest request) {
        return R.ok(importService.parse(request.getSourceHtml(), request.getSourceType()));
    }

    @GetMapping
    public R<Page<PlatformRenderTemplate>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size) {
        return R.ok(templateService.page(current, size));
    }

    @GetMapping("/{id}")
    public R<PlatformRenderTemplate> get(@PathVariable Long id) {
        return R.ok(templateService.get(id));
    }

    @PostMapping
    public R<PlatformRenderTemplate> create(@Valid @RequestBody TemplateSaveRequest request) {
        return R.ok(templateService.create(request));
    }

    @PutMapping("/{id}")
    public R<PlatformRenderTemplate> update(@PathVariable Long id,
                                            @Valid @RequestBody TemplateUpdateRequest request) {
        return R.ok(templateService.update(id, request));
    }

    @GetMapping("/{id}/current-version")
    public R<PlatformRenderTemplateVersion> currentVersion(@PathVariable Long id) {
        return R.ok(templateService.currentVersion(id));
    }

    @PostMapping("/{id}/versions")
    public R<PlatformRenderTemplateVersion> createVersion(@PathVariable Long id,
                                                          @Valid @RequestBody TemplateVersionSaveRequest request) {
        return R.ok(templateService.createVersion(id, request));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        templateService.updateStatus(id, status);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }
}
