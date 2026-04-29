package com.huanjing.geo.module.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.project.dto.KeywordGroupListItemVO;
import com.huanjing.geo.module.project.dto.KeywordGroupPayloadRequest;
import com.huanjing.geo.module.project.dto.KeywordGroupVO;
import com.huanjing.geo.module.project.dto.KeywordLlmQuestionGenerateRequest;
import com.huanjing.geo.module.project.dto.KeywordLlmQuestionGenerateVO;
import com.huanjing.geo.module.project.dto.KeywordPreviewVO;
import com.huanjing.geo.module.project.dto.KeywordTypeConfigVO;
import com.huanjing.geo.module.project.service.KeywordGroupService;
import com.huanjing.geo.module.project.service.KeywordLlmQuestionService;
import com.huanjing.geo.module.project.service.KeywordTypeConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "KeywordGroup")
@RestController
@RequestMapping("/api/keyword-groups")
@RequiredArgsConstructor
public class KeywordGroupController {

    private final KeywordGroupService keywordGroupService;
    private final KeywordTypeConfigService keywordTypeConfigService;
    private final KeywordLlmQuestionService keywordLlmQuestionService;

    @GetMapping
    public R<Page<KeywordGroupListItemVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String type
    ) {
        return R.ok(keywordGroupService.page(current, size, keyword, companyId, projectId, type));
    }

    @GetMapping("/type-configs")
    public R<List<KeywordTypeConfigVO>> getTypeConfigs() {
        return R.ok(keywordTypeConfigService.listConfigs());
    }

    @GetMapping("/{id:\\d+}")
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

    @PostMapping("/llm-questions/generate")
    public R<KeywordLlmQuestionGenerateVO> generateLlmQuestions(@Valid @RequestBody KeywordLlmQuestionGenerateRequest req) {
        return R.ok(keywordLlmQuestionService.generate(req.getCompanyId(), req.getSeedText(), req.getCurrentToken(), req.getCount(), req.getCurrentLlmCount(), req.getTargetCount()));
    }
}
