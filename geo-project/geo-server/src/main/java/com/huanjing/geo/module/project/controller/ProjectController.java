package com.huanjing.geo.module.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaAutoScheduleRequest;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaScheduleConfigRequest;
import com.huanjing.geo.module.content.service.ProjectSelfMediaScheduleService;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleBatchVO;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleBatchDetailVO;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleConfigVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishAutoScheduleResponse;
import com.huanjing.geo.module.project.dto.KeywordGroupImportResultVO;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationQuotaVO;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectCreateRequest;
import com.huanjing.geo.module.project.dto.ProjectFlowUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectKeywordGroupQuotaVO;
import com.huanjing.geo.module.project.dto.ProjectStatusUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectStageUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectUpdateRequest;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.service.ProjectService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Project")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    private final com.huanjing.geo.module.project.service.KeywordGroupService keywordGroupService;

    private final ProjectSelfMediaScheduleService projectSelfMediaScheduleService;

    @GetMapping
    public R<Page<Project>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) Long brandId
    ) {
        return R.ok(projectService.page(current, size, keyword, status, stage, partnerId, brandId));
    }

    @GetMapping("/channel-allocation-quota")
    public R<ProjectChannelAllocationQuotaVO> channelAllocationQuota(@RequestParam Long companyId,
                                                                    @RequestParam(required = false) Long excludeProjectId) {
        return R.ok(projectService.channelAllocationQuota(companyId, excludeProjectId));
    }

    @GetMapping("/keyword-group-quota")
    public R<ProjectKeywordGroupQuotaVO> keywordGroupQuota(@RequestParam Long companyId,
                                                          @RequestParam(required = false) Long excludeProjectId) {
        return R.ok(projectService.keywordGroupQuota(companyId, excludeProjectId));
    }

    @GetMapping("/{id:\\d+}")
    public R<Project> detail(@PathVariable Long id) {
        return R.ok(projectService.detail(id));
    }

    @GetMapping("/{id:\\d+}/self-media-schedule-config")
    public R<ProjectSelfMediaScheduleConfigVO> selfMediaScheduleConfig(@PathVariable Long id) {
        return R.ok(projectSelfMediaScheduleService.getConfig(id));
    }

    @PutMapping("/{id:\\d+}/self-media-schedule-config")
    public R<ProjectSelfMediaScheduleConfigVO> updateSelfMediaScheduleConfig(
            @PathVariable Long id,
            @RequestBody(required = false) ProjectSelfMediaScheduleConfigRequest request) {
        return R.ok(projectSelfMediaScheduleService.updateConfig(id, request));
    }

    @GetMapping("/{id:\\d+}/self-media-schedule-batches/{targetMonth}")
    public R<ProjectSelfMediaScheduleBatchVO> selfMediaScheduleBatch(@PathVariable Long id,
                                                                     @PathVariable String targetMonth) {
        return R.ok(projectSelfMediaScheduleService.getBatch(id, targetMonth));
    }

    @GetMapping("/{id:\\d+}/self-media-schedule-batches/{targetMonth}/detail")
    public R<ProjectSelfMediaScheduleBatchDetailVO> selfMediaScheduleBatchDetail(@PathVariable Long id,
                                                                                 @PathVariable String targetMonth) {
        return R.ok(projectSelfMediaScheduleService.getBatchDetail(id, targetMonth));
    }

    @PostMapping("/{id:\\d+}/self-media-schedule-batches/{targetMonth}/retry-failed")
    public R<ProjectSelfMediaScheduleBatchDetailVO> retryFailedSelfMediaScheduleBatchItems(@PathVariable Long id,
                                                                                           @PathVariable String targetMonth) {
        return R.ok(projectSelfMediaScheduleService.retryFailedItems(id, targetMonth));
    }

    @PostMapping("/{id:\\d+}/self-media-schedules/auto-preview")
    public R<SelfMediaPublishAutoScheduleResponse> previewSelfMediaAutoSchedules(
            @PathVariable Long id,
            @Valid @RequestBody ProjectSelfMediaAutoScheduleRequest request) {
        return R.ok(projectSelfMediaScheduleService.previewForProject(id, request));
    }

    @PostMapping("/{id:\\d+}/self-media-schedules/auto-create")
    public R<SelfMediaPublishAutoScheduleResponse> createSelfMediaAutoSchedules(
            @PathVariable Long id,
            @Valid @RequestBody ProjectSelfMediaAutoScheduleRequest request) {
        return R.ok(projectSelfMediaScheduleService.createForProject(
                id,
                request,
                ProjectSelfMediaScheduleService.TRIGGER_MANUAL
        ));
    }

    @PostMapping
    public R<Project> create(@Valid @RequestBody ProjectCreateRequest req) {
        return R.ok(projectService.create(req));
    }

    @PostMapping("/{id:\\d+}/keyword-groups/import")
    public R<KeywordGroupImportResultVO> importKeywordGroup(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return R.ok(keywordGroupService.importProjectKeywordGroup(id, file));
    }

    @PutMapping("/{id:\\d+}")
    public R<Project> update(@PathVariable Long id, @Valid @RequestBody ProjectUpdateRequest req) {
        return R.ok(projectService.update(id, req));
    }

    @PutMapping("/{id:\\d+}/channel-allocations")
    public R<Project> updateChannelAllocations(@PathVariable Long id, @Valid @RequestBody ProjectChannelAllocationUpdateRequest req) {
        return R.ok(projectService.updateChannelAllocations(id, req));
    }

    @PutMapping("/{id:\\d+}/stage")
    public R<Void> updateStage(@PathVariable Long id, @Valid @RequestBody ProjectStageUpdateRequest req) {
        projectService.updateStage(id, req);
        return R.ok();
    }

    @PutMapping("/{id:\\d+}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody ProjectStatusUpdateRequest req) {
        projectService.updateStatus(id, req);
        return R.ok();
    }

    @PutMapping("/{id:\\d+}/flow")
    public R<Void> updateFlow(@PathVariable Long id, @Valid @RequestBody ProjectFlowUpdateRequest req) {
        projectService.updateFlow(id, req);
        return R.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return R.ok();
    }

}
