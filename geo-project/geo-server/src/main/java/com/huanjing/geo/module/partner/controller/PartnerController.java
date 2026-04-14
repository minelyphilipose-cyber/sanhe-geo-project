package com.huanjing.geo.module.partner.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.partner.dto.PartnerAdjustRequest;
import com.huanjing.geo.module.partner.dto.PartnerCreateRequest;
import com.huanjing.geo.module.partner.dto.PartnerCreateResult;
import com.huanjing.geo.module.partner.dto.PartnerRechargeRequest;
import com.huanjing.geo.module.partner.dto.PartnerStatusUpdateRequest;
import com.huanjing.geo.module.partner.dto.PartnerUpdateRequest;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import com.huanjing.geo.module.partner.entity.PartnerAccountTxn;
import com.huanjing.geo.module.partner.service.PartnerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Partner")
@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    @GetMapping
    public R<Page<Partner>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return R.ok(partnerService.page(current, size, keyword, status));
    }

    @GetMapping("/{id}")
    public R<Partner> detail(@PathVariable Long id) {
        return R.ok(partnerService.detail(id));
    }

    @PostMapping
    public R<PartnerCreateResult> create(@Valid @RequestBody PartnerCreateRequest req) {
        return R.ok(partnerService.create(req));
    }

    @PutMapping("/{id}")
    public R<Partner> update(@PathVariable Long id, @Valid @RequestBody PartnerUpdateRequest req) {
        return R.ok(partnerService.update(id, req));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody PartnerStatusUpdateRequest req) {
        partnerService.updateStatus(id, req.getStatus());
        return R.ok();
    }

    @GetMapping("/{id}/account")
    public R<PartnerAccount> account(@PathVariable Long id) {
        return R.ok(partnerService.account(id));
    }

    @GetMapping("/{id}/account/txns")
    public R<Page<PartnerAccountTxn>> txns(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String txnType,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        return R.ok(partnerService.accountTxns(id, current, size, txnType, bizType, dateFrom, dateTo));
    }

    @PostMapping("/{id}/account/recharge")
    public R<PartnerAccountTxn> recharge(@PathVariable Long id, @Valid @RequestBody PartnerRechargeRequest req) {
        return R.ok(partnerService.recharge(id, req));
    }

    @PostMapping("/{id}/account/adjust")
    public R<PartnerAccountTxn> adjust(@PathVariable Long id, @Valid @RequestBody PartnerAdjustRequest req) {
        return R.ok(partnerService.adjust(id, req));
    }
}
