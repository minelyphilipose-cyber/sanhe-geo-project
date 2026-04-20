package com.huanjing.geo.module.dashboard.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.dashboard.service.ProjectDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Tag(name = "ProjectDashboardPublic")
@RestController
@RequestMapping("/api/public/dashboard")
@RequiredArgsConstructor
public class ProjectDashboardPublicController {

    private final ProjectDashboardService projectDashboardService;

    @GetMapping("/{shareCode}/summary")
    public R<Map<String, Object>> summary(@PathVariable String shareCode) {
        return R.ok(projectDashboardService.getSummary(shareCode));
    }

    @GetMapping("/{shareCode}/trend")
    public R<Map<String, Object>> trend(@PathVariable String shareCode,
                                        @RequestParam(defaultValue = "30") Integer days) {
        return R.ok(projectDashboardService.getTrend(shareCode, days));
    }

    @GetMapping("/{shareCode}/details")
    public R<Map<String, Object>> details(@PathVariable String shareCode,
                                          @RequestParam(defaultValue = "1") Long page,
                                          @RequestParam(defaultValue = "20") Long size,
                                          @RequestParam(required = false) String platformCode,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                          @RequestParam(required = false) String keyword) {
        return R.ok(projectDashboardService.getDetails(shareCode, page, size, platformCode, startDate, endDate, keyword));
    }
}
