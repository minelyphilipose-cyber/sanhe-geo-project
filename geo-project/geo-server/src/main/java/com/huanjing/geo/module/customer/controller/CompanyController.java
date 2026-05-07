package com.huanjing.geo.module.customer.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.customer.dto.CompanyCreateRequest;
import com.huanjing.geo.module.customer.dto.CompanyDeductRequest;
import com.huanjing.geo.module.customer.dto.CompanyDistributionQuotaVO;
import com.huanjing.geo.module.customer.dto.CompanyPackageBindRequest;
import com.huanjing.geo.module.customer.dto.CompanyQuestionPoolQuotaVO;
import com.huanjing.geo.module.customer.dto.CompanyRechargeRequest;
import com.huanjing.geo.module.customer.dto.CompanyUpdateRequest;
import com.huanjing.geo.module.customer.entity.CompanyAccount;
import com.huanjing.geo.module.customer.entity.CompanyAccountTxn;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.customer.service.CompanyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Company")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyPackageBindingService companyPackageBindingService;

    @GetMapping
    public R<Page<Company>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) Long partnerId
    ) {
        return R.ok(companyService.page(current, size, keyword, ownerType, partnerId));
    }

    @GetMapping("/{id}")
    public R<Company> detail(@PathVariable Long id) {
        return R.ok(companyService.detail(id));
    }

    @PostMapping
    public R<Company> create(@Valid @RequestBody CompanyCreateRequest req) {
        return R.ok(companyService.create(req));
    }

    @PutMapping("/{id}")
    public R<Company> update(@PathVariable Long id, @Valid @RequestBody CompanyUpdateRequest req) {
        return R.ok(companyService.update(id, req));
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

    @GetMapping("/{id}/question-pool-quota")
    public R<CompanyQuestionPoolQuotaVO> questionPoolQuota(@PathVariable Long id) {
        return R.ok(companyService.questionPoolQuota(id));
    }

    @GetMapping("/{id}/distribution-quotas")
    public R<CompanyDistributionQuotaVO> distributionQuotas(@PathVariable Long id) {
        return R.ok(companyService.distributionQuotas(id));
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
    public R<java.util.List<CompanyPackageBinding>> packageBindings(@PathVariable Long id) {
        return R.ok(companyPackageBindingService.bindings(id));
    }

    @GetMapping("/{id}/package-binding/active")
    public R<CompanyPackageBinding> activePackageBinding(@PathVariable Long id) {
        return R.ok(companyPackageBindingService.activeBinding(id));
    }

    @PostMapping("/{id}/package-binding")
    public R<CompanyPackageBinding> bindPackage(@PathVariable Long id, @Valid @RequestBody CompanyPackageBindRequest req) {
        return R.ok(companyPackageBindingService.bind(id, req.getPackagePlanId()));
    }

    @DeleteMapping("/{id}/package-binding")
    public R<Void> unbindPackage(@PathVariable Long id) {
        companyPackageBindingService.unbind(id);
        return R.ok();
    }
}
