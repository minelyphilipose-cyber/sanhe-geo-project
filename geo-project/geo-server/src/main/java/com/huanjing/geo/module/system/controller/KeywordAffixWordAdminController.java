package com.huanjing.geo.module.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.dto.KeywordAffixWordAdminItemVO;
import com.huanjing.geo.module.system.dto.KeywordAffixWordCreateRequest;
import com.huanjing.geo.module.system.dto.KeywordAffixWordStatusUpdateRequest;
import com.huanjing.geo.module.system.dto.KeywordAffixWordUpdateRequest;
import com.huanjing.geo.module.system.service.KeywordAffixWordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "KeywordAffixWordAdmin")
@RestController
@RequestMapping("/api/admin/keyword-affix-words")
@RequiredArgsConstructor
public class KeywordAffixWordAdminController {

    private final KeywordAffixWordService keywordAffixWordService;

    @GetMapping
    public R<Page<KeywordAffixWordAdminItemVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String affixKind,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled
    ) {
        return R.ok(keywordAffixWordService.page(current, size, type, affixKind, keyword, enabled));
    }

    @PostMapping
    public R<KeywordAffixWordAdminItemVO> create(@Valid @RequestBody KeywordAffixWordCreateRequest req) {
        return R.ok(keywordAffixWordService.create(req));
    }

    @PutMapping("/{id}")
    public R<KeywordAffixWordAdminItemVO> update(@PathVariable Long id, @Valid @RequestBody KeywordAffixWordUpdateRequest req) {
        return R.ok(keywordAffixWordService.update(id, req));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody KeywordAffixWordStatusUpdateRequest req) {
        keywordAffixWordService.updateStatus(id, req.getEnabled());
        return R.ok();
    }
}
