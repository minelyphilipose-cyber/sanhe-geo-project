package com.huanjing.geo.module.presale.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.presale.dto.request.PresaleBenchmarkSaveRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleBenchmarkStatusRequest;
import com.huanjing.geo.module.presale.persist.entity.PresaleBenchmark;
import com.huanjing.geo.module.presale.persist.entity.PresaleBenchmarkHistory;
import com.huanjing.geo.module.presale.service.PresaleBenchmarkAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/presale/benchmarks")
@RequiredArgsConstructor
public class PresaleBenchmarkController {
    private final PresaleBenchmarkAdminService service;

    @GetMapping
    public R<List<PresaleBenchmark>> list() {
        return R.ok(service.list());
    }

    @GetMapping("/history")
    public R<List<PresaleBenchmarkHistory>> history(@RequestParam(required = false) Long benchmarkId) {
        return R.ok(service.history(benchmarkId));
    }

    @PostMapping
    public R<PresaleBenchmark> create(@Valid @RequestBody PresaleBenchmarkSaveRequest request) {
        return R.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public R<PresaleBenchmark> update(@PathVariable Long id,
                                      @Valid @RequestBody PresaleBenchmarkSaveRequest request) {
        return R.ok(service.update(id, request));
    }

    @PutMapping("/{id}/status")
    public R<PresaleBenchmark> updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody PresaleBenchmarkStatusRequest request) {
        return R.ok(service.updateStatus(id, request));
    }
}
