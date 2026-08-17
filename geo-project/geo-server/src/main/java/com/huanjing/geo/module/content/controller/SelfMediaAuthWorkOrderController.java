package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.SelfMediaAuthWorkOrderUpdateRequest;
import com.huanjing.geo.module.content.dto.SelfMediaAuthWorkOrderEventVO;
import com.huanjing.geo.module.content.dto.SelfMediaAuthWorkOrderPageVO;
import com.huanjing.geo.module.content.dto.SelfMediaAuthWorkOrderVO;
import com.huanjing.geo.module.content.service.SelfMediaAuthWorkOrderService;
import com.huanjing.geo.module.partner.service.PartnerFeatureAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/content/account-auth-health/work-orders")
@RequiredArgsConstructor
public class SelfMediaAuthWorkOrderController {
    private final SelfMediaAuthWorkOrderService workOrderService;
    private final PartnerFeatureAccessGuard partnerFeatureAccessGuard;

    @GetMapping
    public R<SelfMediaAuthWorkOrderPageVO> list(@RequestParam(required = false) String stage,
                                                @RequestParam(required = false) Long ownerUserId,
                                                @RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size) {
        ensureAccess();
        return R.ok(workOrderService.page(stage, ownerUserId, current, size));
    }

    @PostMapping
    public R<SelfMediaAuthWorkOrderVO> getOrCreate(@RequestBody Map<String, Object> request) {
        ensureAccess();
        return R.ok(workOrderService.getOrCreate(
                number(request.get("brandId")),
                number(request.get("browserEnvironmentId")),
                request.get("issueSummary") == null ? null : String.valueOf(request.get("issueSummary"))));
    }

    @PatchMapping("/{id}")
    public R<SelfMediaAuthWorkOrderVO> update(@PathVariable Long id,
                                              @Valid @RequestBody SelfMediaAuthWorkOrderUpdateRequest request) {
        ensureAccess();
        return R.ok(workOrderService.update(id, request));
    }

    @GetMapping("/{id}/events")
    public R<List<SelfMediaAuthWorkOrderEventVO>> events(@PathVariable Long id) {
        ensureAccess();
        return R.ok(workOrderService.events(id));
    }

    @PostMapping("/{id}/claim")
    public R<SelfMediaAuthWorkOrderVO> claim(@PathVariable Long id) {
        ensureAccess();
        return R.ok(workOrderService.claim(id));
    }

    private Long number(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(String.valueOf(value));
    }

    private void ensureAccess() {
        partnerFeatureAccessGuard.ensureInternalDeliveryFeature("account auth health work orders");
    }
}
