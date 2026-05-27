package com.huanjing.geo.module.delivery.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.delivery.dto.DeliveryExceptionHandleRequest;
import com.huanjing.geo.module.delivery.dto.DeliveryExceptionVO;
import com.huanjing.geo.module.delivery.dto.DeliveryOperatorStatsVO;
import com.huanjing.geo.module.delivery.dto.DeliveryOverviewVO;
import com.huanjing.geo.module.delivery.service.DeliveryDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Delivery Dashboard")
@RestController
@RequestMapping("/api/delivery/dashboard")
@RequiredArgsConstructor
public class DeliveryDashboardController {

    private final DeliveryDashboardService deliveryDashboardService;

    @GetMapping("/overview")
    public R<DeliveryOverviewVO> overview() {
        return R.ok(deliveryDashboardService.overview());
    }

    @GetMapping("/operator-stats")
    public R<List<DeliveryOperatorStatsVO>> operatorStats() {
        return R.ok(deliveryDashboardService.operatorStats());
    }

    @GetMapping("/exceptions")
    public R<Page<DeliveryExceptionVO>> exceptions(@RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "20") long size,
                                                   @RequestParam(required = false) String severity,
                                                   @RequestParam(required = false) String status) {
        return R.ok(deliveryDashboardService.exceptions(current, size, severity, status));
    }

    @PostMapping("/exceptions/{id}/handle")
    public R<Void> handleException(@PathVariable Long id,
                                   @RequestBody(required = false) DeliveryExceptionHandleRequest req) {
        deliveryDashboardService.handleException(id, req == null ? null : req.getNote());
        return R.ok();
    }
}
