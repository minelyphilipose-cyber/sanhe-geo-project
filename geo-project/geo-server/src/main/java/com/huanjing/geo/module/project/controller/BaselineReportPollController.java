package com.huanjing.geo.module.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.project.dto.BaselinePollBatchVO;
import com.huanjing.geo.module.project.dto.BaselinePollOptionsVO;
import com.huanjing.geo.module.project.dto.BaselinePollResultVO;
import com.huanjing.geo.module.project.dto.BaselinePollStartRequest;
import com.huanjing.geo.module.project.service.BaselineReportPollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId:\\d+}/baseline-report")
@RequiredArgsConstructor
public class BaselineReportPollController {
    private final BaselineReportPollService baselineReportPollService;

    @GetMapping("/options")
    public R<BaselinePollOptionsVO> options(@PathVariable Long projectId) {
        return R.ok(baselineReportPollService.options(projectId));
    }

    @PostMapping("/poll")
    public R<BaselinePollBatchVO> start(@PathVariable Long projectId,
                                        @Valid @RequestBody BaselinePollStartRequest request) {
        return R.ok(baselineReportPollService.start(projectId, request));
    }

    @GetMapping("/results")
    public R<Page<BaselinePollResultVO>> results(@PathVariable Long projectId,
                                                 @RequestParam(required = false) Long batchId,
                                                 @RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "20") long size) {
        return R.ok(baselineReportPollService.results(projectId, batchId, current, size));
    }
}
