package com.huanjing.geo.module.project.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaAutoScheduleRequest;
import com.huanjing.geo.module.content.service.ProjectSelfMediaPlanningService;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaPlanningTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectSelfMediaPlanningController {
    private final ProjectSelfMediaPlanningService planningService;

    @PostMapping("/{id:\\d+}/self-media-schedules/auto-create-async")
    public R<ProjectSelfMediaPlanningTaskVO> submit(@PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ProjectSelfMediaAutoScheduleRequest request) {
        return R.ok(planningService.submit(id, request, key));
    }

    @GetMapping("/{id:\\d+}/self-media-planning-tasks/{targetMonth}/latest")
    public R<ProjectSelfMediaPlanningTaskVO> latest(@PathVariable Long id, @PathVariable String targetMonth) {
        return R.ok(planningService.latest(id, targetMonth));
    }
}
