package com.huanjing.geo.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.ArticleGenerationOptionDtos.AllocationPreviewRequest;
import com.huanjing.geo.module.content.dto.ArticleGenerationOptionDtos.AllocationPreviewResponse;
import com.huanjing.geo.module.content.dto.ArticleGenerationOptionDtos.GenerationOptionsVO;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.TemplateDetailVO;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.TemplateSaveRequest;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.TemplateVO;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.VariableVO;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.VersionCreateRequest;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.WeightUpdateRequest;
import com.huanjing.geo.module.content.service.ArticlePromptTemplateService;
import com.huanjing.geo.module.content.service.ArticleTemplateAllocationService;
import com.huanjing.geo.module.content.service.TemplatePerspectiveService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/content/article-prompt-templates")
@RequiredArgsConstructor
public class ArticlePromptTemplateController {

    private final ArticlePromptTemplateService templateService;
    private final ArticleTemplateAllocationService allocationService;
    private final TemplatePerspectiveService perspectiveService;
    private final ProjectMapper projectMapper;

    @GetMapping
    public R<Page<TemplateVO>> page(@RequestParam(required = false) String channelGroupCode,
                                    @RequestParam(required = false) String channelSubCode,
                                    @RequestParam(required = false) String agentSiteModule,
                                    @RequestParam(required = false) String questionSceneCode,
                                    @RequestParam(required = false) String perspectiveCode,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "1") long current,
                                    @RequestParam(defaultValue = "10") long size) {
        return R.ok(templateService.page(channelGroupCode, channelSubCode, agentSiteModule, questionSceneCode,
                perspectiveCode, status, keyword, current, size));
    }

    @GetMapping("/variables")
    public R<java.util.List<VariableVO>> variables() {
        return R.ok(templateService.variables().stream()
                .map(def -> new VariableVO(
                        def.code(),
                        def.name(),
                        def.description(),
                        def.source(),
                        def.emptyStrategy(),
                        def.emptyText(),
                        def.sampleValue()
                ))
                .toList());
    }

    @GetMapping("/{id}")
    public R<TemplateDetailVO> detail(@PathVariable Long id) {
        return R.ok(templateService.detail(id));
    }

    @PostMapping
    public R<TemplateDetailVO> create(@Valid @RequestBody TemplateSaveRequest req) {
        return R.ok(templateService.create(req));
    }

    @PutMapping("/{id}")
    public R<TemplateDetailVO> update(@PathVariable Long id, @Valid @RequestBody TemplateSaveRequest req) {
        return R.ok(templateService.update(id, req));
    }

    @PatchMapping("/{id}/weight")
    public R<TemplateVO> updateWeight(@PathVariable Long id, @Valid @RequestBody WeightUpdateRequest req) {
        return R.ok(templateService.updateWeight(id, req));
    }

    @PostMapping("/{id}/versions")
    public R<TemplateDetailVO> createVersion(@PathVariable Long id, @Valid @RequestBody VersionCreateRequest req) {
        return R.ok(templateService.createVersion(id, req));
    }

    @PostMapping("/{id}/versions/{versionId}/publish")
    public R<TemplateDetailVO> publishVersion(@PathVariable Long id, @PathVariable Long versionId) {
        return R.ok(templateService.publishVersion(id, versionId));
    }

    @GetMapping("/generation-options")
    public R<GenerationOptionsVO> generationOptions() {
        return R.ok(allocationService.options());
    }

    @PostMapping("/preview-allocation")
    public R<AllocationPreviewResponse> previewAllocation(@Valid @RequestBody AllocationPreviewRequest req) {
        TemplatePerspectiveService.ResolvedPerspective perspective = resolvePreviewPerspective(req);
        return R.ok(allocationService.preview(req.channelGroupCode(), req.channelSubCode(), req.questionSceneCode(),
                perspective.perspectiveCode(), req.count()));
    }

    private TemplatePerspectiveService.ResolvedPerspective resolvePreviewPerspective(AllocationPreviewRequest req) {
        if (req.projectId() == null) {
            return TemplatePerspectiveService.ResolvedPerspective.customer();
        }
        Project project = projectMapper.selectById(req.projectId());
        Long brandId = project == null ? null : project.getBrandId();
        return perspectiveService.resolve(brandId, req.channelGroupCode(), req.channelSubCode());
    }
}
