package com.huanjing.geo.module.project.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.project.dto.BaselinePrintRenderResponse;
import com.huanjing.geo.module.project.service.BaselineReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/baseline-report/exports/render")
@RequiredArgsConstructor
public class BaselinePrintRenderController {
    private final BaselineReportExportService exportService;

    @GetMapping("/{renderToken}")
    public R<BaselinePrintRenderResponse> renderPayload(@PathVariable String renderToken) {
        return R.ok(exportService.getRenderPayload(renderToken));
    }
}
