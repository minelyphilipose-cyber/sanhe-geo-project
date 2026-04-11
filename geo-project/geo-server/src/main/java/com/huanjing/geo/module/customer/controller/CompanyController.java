package com.huanjing.geo.module.customer.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.customer.dto.CompanyCreateRequest;
import com.huanjing.geo.module.customer.dto.CompanyUpdateRequest;
import com.huanjing.geo.module.customer.entity.Company;
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
}
