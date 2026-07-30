package com.huanjing.geo.module.customer.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.customer.dto.CompanyCreateRequest;
import com.huanjing.geo.module.customer.dto.CompanyDeductRequest;
import com.huanjing.geo.module.customer.dto.CompanyKeywordGroupQuotaVO;
import com.huanjing.geo.module.customer.dto.CompanyOwnerTransferRequest;
import com.huanjing.geo.module.customer.dto.CompanyPackageBindRequest;
import com.huanjing.geo.module.customer.dto.CompanyPartnerEntryReturnRequest;
import com.huanjing.geo.module.customer.dto.CompanyPartnerStaffAssignRequest;
import com.huanjing.geo.module.customer.dto.CompanyRechargeRequest;
import com.huanjing.geo.module.customer.dto.CompanyUpdateRequest;
import com.huanjing.geo.module.customer.dto.SalesOwnerOptionVO;
import com.huanjing.geo.module.customer.entity.CompanyAccount;
import com.huanjing.geo.module.customer.entity.CompanyAccountTxn;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.customer.service.CompanyService;
import com.huanjing.geo.module.partner.service.PartnerResponseSanitizer;
import com.huanjing.geo.module.project.dto.PartnerSubmissionReadinessVO;
import com.huanjing.geo.module.project.service.ProjectStartRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Company")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyPackageBindingService companyPackageBindingService;
    private final PartnerResponseSanitizer partnerResponseSanitizer;
    private final ProjectStartRequestService projectStartRequestService;

    @GetMapping
    public R<?> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) Long partnerId
    ) {
        return R.ok(partnerResponseSanitizer.companyPage(companyService.page(current, size, keyword, ownerType, partnerId)));
    }

    @GetMapping("/{id}")
    public R<?> detail(@PathVariable Long id) {
        return R.ok(partnerResponseSanitizer.company(companyService.detail(id)));
    }

    @GetMapping("/sales-owner-options")
    public R<List<SalesOwnerOptionVO>> salesOwnerOptions() {
        return R.ok(companyService.salesOwnerOptions());
    }

    @GetMapping("/delivery-owner-options")
    public R<List<SalesOwnerOptionVO>> deliveryOwnerOptions() {
        return R.ok(companyService.deliveryOwnerOptions());
    }

    @PostMapping
    public R<?> create(@Valid @RequestBody CompanyCreateRequest req) {
        return R.ok(partnerResponseSanitizer.company(companyService.create(req)));
    }

    @PutMapping("/{id}")
    public R<?> update(@PathVariable Long id, @Valid @RequestBody CompanyUpdateRequest req) {
        return R.ok(partnerResponseSanitizer.company(companyService.update(id, req)));
    }

    @PostMapping("/{id}/owner-transfer")
    public R<?> transferOwner(@PathVariable Long id, @Valid @RequestBody CompanyOwnerTransferRequest req) {
        return R.ok(partnerResponseSanitizer.company(companyService.transferOwner(id, req)));
    }

    @PostMapping("/{id}/partner-staff-owner")
    public R<?> assignPartnerStaffOwner(@PathVariable Long id, @Valid @RequestBody CompanyPartnerStaffAssignRequest req) {
        return R.ok(partnerResponseSanitizer.company(companyService.assignPartnerStaffOwner(id, req)));
    }

    @PostMapping("/{id}/partner-workflow/request-package")
    public R<?> requestPartnerPackage(@PathVariable Long id) {
        return R.ok(partnerResponseSanitizer.company(companyService.requestPartnerPackage(id)));
    }

    @PostMapping("/{id}/partner-workflow/notify-entry")
    public R<?> notifyPartnerStaffForProjectEntry(@PathVariable Long id) {
        return R.ok(partnerResponseSanitizer.company(companyService.notifyPartnerStaffForProjectEntry(id)));
    }

    @PostMapping("/{id}/partner-workflow/complete-entry")
    public R<?> completePartnerEntry(@PathVariable Long id) {
        return R.ok(partnerResponseSanitizer.company(companyService.completePartnerEntry(id)));
    }

    @PostMapping("/{id}/partner-workflow/return-entry")
    public R<?> returnPartnerEntry(@PathVariable Long id, @Valid @RequestBody CompanyPartnerEntryReturnRequest req) {
        return R.ok(partnerResponseSanitizer.company(companyService.returnPartnerEntry(id, req)));
    }

    @GetMapping("/{id}/partner-workflow/submission-readiness")
    public R<PartnerSubmissionReadinessVO> partnerSubmissionReadiness(@PathVariable Long id) {
        return R.ok(projectStartRequestService.partnerSubmissionReadiness(id));
    }

    @GetMapping("/{id}/partner-staff-options")
    public R<List<SalesOwnerOptionVO>> partnerStaffOptions(@PathVariable Long id) {
        return R.ok(companyService.partnerStaffOptions(id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        companyService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/account")
    public R<CompanyAccount> account(@PathVariable Long id) {
        return R.ok(companyService.account(id));
    }

    @GetMapping("/{id}/keyword-group-quota")
    public R<?> keywordGroupQuota(@PathVariable Long id) {
        return R.ok(partnerResponseSanitizer.companyKeywordGroupQuota(companyService.keywordGroupQuota(id)));
    }

    @GetMapping("/{id}/distribution-quotas")
    public R<?> distributionQuotas(@PathVariable Long id) {
        return R.ok(partnerResponseSanitizer.companyDistributionQuotas(companyService.distributionQuotas(id)));
    }

    @GetMapping("/{id}/account/txns")
    public R<Page<CompanyAccountTxn>> txns(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String txnType,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        return R.ok(companyService.accountTxns(id, current, size, txnType, bizType, dateFrom, dateTo));
    }

    @PostMapping("/{id}/account/recharge")
    public R<CompanyAccountTxn> recharge(@PathVariable Long id, @Valid @RequestBody CompanyRechargeRequest req) {
        return R.ok(companyService.recharge(id, req));
    }

    @PostMapping("/{id}/account/deduct")
    public R<CompanyAccountTxn> deduct(@PathVariable Long id, @Valid @RequestBody CompanyDeductRequest req) {
        return R.ok(companyService.deduct(id, req));
    }

    @GetMapping("/{id}/package-bindings")
    public R<?> packageBindings(@PathVariable Long id) {
        return R.ok(partnerResponseSanitizer.companyPackageBindings(companyPackageBindingService.bindings(id)));
    }

    @GetMapping("/{id}/package-binding/active")
    public R<?> activePackageBinding(@PathVariable Long id) {
        return R.ok(partnerResponseSanitizer.companyPackageBinding(companyPackageBindingService.activeBindingForCurrentUser(id)));
    }

    @PostMapping("/{id}/package-binding")
    public R<?> bindPackage(@PathVariable Long id, @Valid @RequestBody CompanyPackageBindRequest req) {
        return R.ok(partnerResponseSanitizer.companyPackageBinding(companyPackageBindingService.bind(id, req.getPackagePlanId())));
    }

    @PostMapping("/{id}/package-binding/refresh")
    public R<?> refreshPackageBinding(@PathVariable Long id) {
        return R.ok(partnerResponseSanitizer.companyPackageBinding(companyPackageBindingService.refreshActiveBinding(id)));
    }

    @DeleteMapping("/{id}/package-binding")
    public R<Void> unbindPackage(@PathVariable Long id) {
        companyPackageBindingService.unbind(id);
        return R.ok();
    }
}
