package com.huanjing.geo.module.presale.export.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.presale.export.dto.PresalePrintRenderResponse;
import com.huanjing.geo.module.presale.export.service.PresaleReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presale/exports/render")
@RequiredArgsConstructor
public class PresalePrintRenderController {
    private final PresaleReportExportService exportService;

    @GetMapping("/{renderToken}")
    public R<PresalePrintRenderResponse> renderPayload(@PathVariable String renderToken) {
        return R.ok(exportService.getRenderPayload(renderToken));
    }
}