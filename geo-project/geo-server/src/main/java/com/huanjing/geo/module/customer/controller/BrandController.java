package com.huanjing.geo.module.customer.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.customer.dto.BrandCreateRequest;
import com.huanjing.geo.module.customer.dto.BrandUpdateRequest;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.service.BrandService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Brand")
@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public R<Page<Brand>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword
    ) {
        return R.ok(brandService.page(current, size, companyId, keyword));
    }

    @GetMapping("/{id}")
    public R<Brand> detail(@PathVariable Long id) {
        return R.ok(brandService.detail(id));
    }

    @PostMapping
    public R<Brand> create(@Valid @RequestBody BrandCreateRequest req) {
        return R.ok(brandService.create(req));
    }

    @PutMapping("/{id}")
    public R<Brand> update(@PathVariable Long id, @Valid @RequestBody BrandUpdateRequest req) {
        return R.ok(brandService.update(id, req));
    }
}
