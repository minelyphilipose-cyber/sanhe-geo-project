package com.huanjing.geo.module.partner.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.partner.dto.PartnerAdjustRequest;
import com.huanjing.geo.module.partner.dto.PartnerCreateRequest;
import com.huanjing.geo.module.partner.dto.PartnerCreateResult;
import com.huanjing.geo.module.partner.dto.PartnerRechargeApplyRequest;
import com.huanjing.geo.module.partner.dto.PartnerRechargeAuditRequest;
import com.huanjing.geo.module.partner.dto.PartnerRechargeRequest;
import com.huanjing.geo.module.partner.dto.PartnerStaffCreateRequest;
import com.huanjing.geo.module.partner.dto.PartnerStaffCreateResult;
import com.huanjing.geo.module.partner.dto.PartnerStaffResetPasswordResult;
import com.huanjing.geo.module.partner.dto.PartnerStaffStatusRequest;
import com.huanjing.geo.module.partner.dto.PartnerStaffUpdateRequest;
import com.huanjing.geo.module.partner.dto.PartnerStaffVO;
import com.huanjing.geo.module.partner.dto.PartnerStatusUpdateRequest;
import com.huanjing.geo.module.partner.dto.PartnerUpdateRequest;
import com.huanjing.geo.module.partner.dto.PartnerVoucherFile;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import com.huanjing.geo.module.partner.entity.PartnerAccountTxn;
import com.huanjing.geo.module.partner.entity.PartnerRechargeOrder;
import com.huanjing.geo.module.partner.service.PartnerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

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

    @GetMapping("/me/staff")
    public R<List<PartnerStaffVO>> myStaff() {
        return R.ok(partnerService.myStaff());
    }

    @PostMapping("/me/staff")
    public R<PartnerStaffCreateResult> createMyStaff(@Valid @RequestBody PartnerStaffCreateRequest req) {
        return R.ok(partnerService.createMyStaff(req));
    }

    @PutMapping("/me/staff/{staffUserId}")
    public R<PartnerStaffVO> updateMyStaff(
            @PathVariable Long staffUserId,
            @Valid @RequestBody PartnerStaffUpdateRequest req
    ) {
        return R.ok(partnerService.updateMyStaff(staffUserId, req));
    }

    @PutMapping("/me/staff/{staffUserId}/status")
    public R<PartnerStaffVO> updateMyStaffStatus(
            @PathVariable Long staffUserId,
            @Valid @RequestBody PartnerStaffStatusRequest req
    ) {
        return R.ok(partnerService.updateMyStaffStatus(staffUserId, req.getIsActive()));
    }

    @DeleteMapping("/me/staff/{staffUserId}")
    public R<Void> deleteMyStaff(@PathVariable Long staffUserId) {
        partnerService.deleteMyStaff(staffUserId);
        return R.ok();
    }

    @PostMapping("/me/staff/{staffUserId}/reset-password")
    public R<PartnerStaffResetPasswordResult> resetMyStaffPassword(@PathVariable Long staffUserId) {
        return R.ok(partnerService.resetMyStaffPassword(staffUserId));
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

    @GetMapping("/{id}/account/recharge-orders")
    public R<Page<PartnerRechargeOrder>> rechargeOrders(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status
    ) {
        return R.ok(partnerService.rechargeOrders(id, current, size, status));
    }

    @PostMapping("/{id}/account/recharge-orders")
    public R<PartnerRechargeOrder> applyRecharge(@PathVariable Long id, @Valid @RequestBody PartnerRechargeApplyRequest req) {
        return R.ok(partnerService.applyRecharge(id, req));
    }

    @PostMapping("/{id}/account/recharge-orders/{orderId}/cancel")
    public R<Void> cancelRechargeOrder(@PathVariable Long id, @PathVariable Long orderId) {
        partnerService.cancelRechargeOrder(id, orderId);
        return R.ok();
    }

    @PostMapping("/{id}/account/recharge-orders/{orderId}/audit")
    public R<PartnerRechargeOrder> auditRechargeOrder(
            @PathVariable Long id,
            @PathVariable Long orderId,
            @Valid @RequestBody PartnerRechargeAuditRequest req
    ) {
        return R.ok(partnerService.auditRechargeOrder(id, orderId, req));
    }

    @PostMapping("/{id}/account/recharge")
    public R<PartnerAccountTxn> recharge(@PathVariable Long id, @Valid @RequestBody PartnerRechargeRequest req) {
        return R.ok(partnerService.recharge(id, req));
    }

    @PostMapping("/{id}/account/vouchers/upload")
    public R<PartnerVoucherFile> uploadAccountVoucher(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return R.ok(partnerService.uploadAccountVoucher(id, file));
    }

    @PostMapping("/account/vouchers/initial-upload")
    public R<PartnerVoucherFile> uploadInitialAccountVoucher(@RequestPart("file") MultipartFile file) {
        return R.ok(partnerService.uploadInitialAccountVoucher(file));
    }

    @GetMapping("/{id}/account/vouchers/download")
    public ResponseEntity<byte[]> downloadAccountVoucher(
            @PathVariable Long id,
            @RequestParam String objectKey
    ) {
        PartnerVoucherFile voucher = partnerService.voucherDetail(id, objectKey);
        byte[] bytes = partnerService.readVoucherBytes(id, objectKey);
        String fileName = voucher.getFileName();
        MediaType mediaType = voucherMediaType(fileName);
        boolean inline = "image".equalsIgnoreCase(mediaType.getType()) || mediaType.includes(MediaType.APPLICATION_PDF);
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }

    private MediaType voucherMediaType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lower.endsWith(".bmp")) {
            return MediaType.parseMediaType("image/bmp");
        }
        return MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    @PostMapping("/{id}/account/adjust")
    public R<PartnerAccountTxn> adjust(@PathVariable Long id, @Valid @RequestBody PartnerAdjustRequest req) {
        return R.ok(partnerService.adjust(id, req));
    }
}
