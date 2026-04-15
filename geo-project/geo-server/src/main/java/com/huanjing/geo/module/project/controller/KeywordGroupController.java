package com.huanjing.geo.module.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.project.dto.KeywordGroupPayloadRequest;
import com.huanjing.geo.module.project.dto.KeywordGroupVO;
import com.huanjing.geo.module.project.dto.KeywordPreviewVO;
import com.huanjing.geo.module.project.service.KeywordGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "KeywordGroup")
@RestController
@RequestMapping("/api/keyword-groups")
@RequiredArgsConstructor
public class KeywordGroupController {

    private final KeywordGroupService keywordGroupService;

    @GetMapping
    public R<Page<KeywordGroupVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type
    ) {
        return R.ok(keywordGroupService.page(current, size, keyword, type));
    }

    @GetMapping("/{id}")
    public R<KeywordGroupVO> detail(@PathVariable Long id) {
        return R.ok(keywordGroupService.detail(id));
    }

    @PostMapping
    public R<KeywordGroupVO> create(@Valid @RequestBody KeywordGroupPayloadRequest req) {
        return R.ok(keywordGroupService.create(req));
    }

    @PutMapping("/{id}")
    public R<KeywordGroupVO> update(@PathVariable Long id, @Valid @RequestBody KeywordGroupPayloadRequest req) {
        return R.ok(keywordGroupService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        keywordGroupService.delete(id);
        return R.ok();
    }

    @PostMapping("/preview")
    public R<KeywordPreviewVO> preview(@Valid @RequestBody KeywordGroupPayloadRequest req) {
        return R.ok(keywordGroupService.preview(req));
    }
}
