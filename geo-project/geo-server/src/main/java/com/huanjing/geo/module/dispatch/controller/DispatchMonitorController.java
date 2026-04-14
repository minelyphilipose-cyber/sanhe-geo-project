package com.huanjing.geo.module.dispatch.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.dispatch.dto.DispatchAlertResolveRequest;
import com.huanjing.geo.module.dispatch.dto.DispatchAlertVO;
import com.huanjing.geo.module.dispatch.dto.DispatchDashboardVO;
import com.huanjing.geo.module.dispatch.dto.DispatchPlatformHealthVO;
import com.huanjing.geo.module.dispatch.dto.DispatchTaskMonitorVO;
import com.huanjing.geo.module.dispatch.service.DispatchMonitorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Dispatch Monitoring")
@RestController
@RequestMapping("/api/dispatch/monitor")
@RequiredArgsConstructor
public class DispatchMonitorController {

    private final DispatchMonitorService dispatchMonitorService;

    @GetMapping("/dashboard")
    public R<DispatchDashboardVO> dashboard(
            @RequestParam(defaultValue = "today") String rangeType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        return R.ok(dispatchMonitorService.dashboard(rangeType, startDate, endDate));
    }

    @GetMapping("/tasks")
    public R<Page<DispatchTaskMonitorVO>> taskPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "today") String rangeType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return R.ok(dispatchMonitorService.taskPage(current, size, rangeType, startDate, endDate, taskType, status, keyword));
    }

    @GetMapping("/platforms")
    public R<List<DispatchPlatformHealthVO>> platformHealth(
            @RequestParam(defaultValue = "today") String rangeType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        return R.ok(dispatchMonitorService.platformHealth(rangeType, startDate, endDate));
    }

    @GetMapping("/alerts")
    public R<Page<DispatchAlertVO>> alertPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "today") String rangeType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status
    ) {
        return R.ok(dispatchMonitorService.alertPage(current, size, rangeType, startDate, endDate, severity, status));
    }

    @PostMapping("/alerts/{id}/resolve")
    public R<Void> resolveAlert(@PathVariable Long id, @Valid @RequestBody(required = false) DispatchAlertResolveRequest req) {
        dispatchMonitorService.resolveAlert(id, req == null ? null : req.getNote());
        return R.ok();
    }
}

