package com.huanjing.geo.module.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.project.dto.ProjectCreateRequest;
import com.huanjing.geo.module.project.dto.ProjectStatusUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectStageUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectUpdateRequest;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.service.ProjectService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Project")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public R<Page<Project>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) Long partnerId
    ) {
        return R.ok(projectService.page(current, size, keyword, status, stage, partnerId));
    }

    @GetMapping("/{id}")
    public R<Project> detail(@PathVariable Long id) {
        return R.ok(projectService.detail(id));
    }

    @PostMapping
    public R<Project> create(@Valid @RequestBody ProjectCreateRequest req) {
        return R.ok(projectService.create(req));
    }

    @PutMapping("/{id}")
    public R<Project> update(@PathVariable Long id, @Valid @RequestBody ProjectUpdateRequest req) {
        return R.ok(projectService.update(id, req));
    }

    @PutMapping("/{id}/stage")
    public R<Void> updateStage(@PathVariable Long id, @Valid @RequestBody ProjectStageUpdateRequest req) {
        projectService.updateStage(id, req);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody ProjectStatusUpdateRequest req) {
        projectService.updateStatus(id, req);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return R.ok();
    }
}
