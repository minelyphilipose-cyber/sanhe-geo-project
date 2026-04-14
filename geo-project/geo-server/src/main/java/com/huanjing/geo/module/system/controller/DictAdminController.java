package com.huanjing.geo.module.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.dto.DictItemAdminVO;
import com.huanjing.geo.module.system.dto.DictItemCreateRequest;
import com.huanjing.geo.module.system.dto.DictItemStatusUpdateRequest;
import com.huanjing.geo.module.system.dto.DictItemUpdateRequest;
import com.huanjing.geo.module.system.service.DictAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "DictAdmin")
@RestController
@RequestMapping("/api/admin/dicts")
@RequiredArgsConstructor
public class DictAdminController {

    private final DictAdminService dictAdminService;

    @GetMapping
    public R<Page<DictItemAdminVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String dictType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled
    ) {
        return R.ok(dictAdminService.page(current, size, dictType, keyword, enabled));
    }

    @GetMapping("/types")
    public R<List<String>> types() {
        return R.ok(dictAdminService.dictTypes());
    }

    @PostMapping
    public R<DictItemAdminVO> create(@Valid @RequestBody DictItemCreateRequest req) {
        return R.ok(dictAdminService.create(req));
    }

    @PutMapping("/{id}")
    public R<DictItemAdminVO> update(@PathVariable Long id, @Valid @RequestBody DictItemUpdateRequest req) {
        return R.ok(dictAdminService.update(id, req));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody DictItemStatusUpdateRequest req) {
        dictAdminService.updateStatus(id, req.getEnabled());
        return R.ok();
    }
}

