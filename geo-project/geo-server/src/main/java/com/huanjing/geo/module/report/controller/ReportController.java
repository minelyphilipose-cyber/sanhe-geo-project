package com.huanjing.geo.module.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.report.dto.*;
import com.huanjing.geo.module.report.entity.Report;
import com.huanjing.geo.module.report.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Report")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public R<Page<Report>> page(@RequestParam(defaultValue = "1") Long current,
                                @RequestParam(defaultValue = "10") Long size,
                                @RequestParam(required = false) Long projectId,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String status) {
        return R.ok(reportService.page(current, size, projectId, keyword, status));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(reportService.detail(id));
    }

    @PutMapping("/{id}/publish")
    public R<Report> publish(@PathVariable Long id, @RequestBody(required = false) ReportPublishRequest req) {
        return R.ok(reportService.publish(id, req == null ? new ReportPublishRequest() : req));
    }

    @PutMapping("/{id}/intercept")
    public R<Report> intercept(@PathVariable Long id, @Valid @RequestBody ReportInterceptRequest req) {
        return R.ok(reportService.intercept(id, req.getReason()));
    }

    @PutMapping("/{id}/pdf/regenerate")
    public R<Report> regeneratePdf(@PathVariable Long id) {
        return R.ok(reportService.regeneratePdf(id));
    }

    @PostMapping("/{id}/regenerate")
    public R<Map<String, Report>> regeneratePostsale(@PathVariable Long id) {
        return R.ok(reportService.regeneratePostsalePair(id));
    }

    @PostMapping("/freeze/quarterly")
    public R<ReportPeriodFreezeResponse> freezeQuarterly() {
        throw retiredQuarterlyFreeze();
    }

    private BizException retiredQuarterlyFreeze() {
        return new BizException(410, "Quarterly report freeze is permanently retired", 410, null);
    }
}
