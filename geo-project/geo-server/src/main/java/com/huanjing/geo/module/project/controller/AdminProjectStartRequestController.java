package com.huanjing.geo.module.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.project.dto.AdminProjectStartRequestVO;
import com.huanjing.geo.module.project.dto.ProjectSetupReadyRequest;
import com.huanjing.geo.module.project.dto.ProjectStartRequestApproveRequest;
import com.huanjing.geo.module.project.dto.ProjectStartRequestRejectRequest;
import com.huanjing.geo.module.project.service.ProjectStartRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/project-start-requests")
@RequiredArgsConstructor
public class AdminProjectStartRequestController {

    private final ProjectStartRequestService startRequestService;

    @GetMapping
    public R<Page<AdminProjectStartRequestVO>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long partnerId,
                                                    @RequestParam(required = false) Long companyId,
                                                    @RequestParam(required = false) Long projectId) {
        return R.ok(startRequestService.adminPage(current, size, status, partnerId, companyId, projectId));
    }

    @GetMapping("/{id:\\d+}")
    public R<AdminProjectStartRequestVO> detail(@PathVariable Long id) {
        return R.ok(startRequestService.adminDetail(id));
    }

    @PostMapping("/{id:\\d+}/approve")
    public R<AdminProjectStartRequestVO> approve(@PathVariable Long id,
                                                 @Valid @RequestBody(required = false) ProjectStartRequestApproveRequest req) {
        return R.ok(startRequestService.approve(id, req));
    }

    @PostMapping("/{id:\\d+}/reject")
    public R<AdminProjectStartRequestVO> reject(@PathVariable Long id,
                                                @Valid @RequestBody ProjectStartRequestRejectRequest req) {
        return R.ok(startRequestService.reject(id, req));
    }

    @PostMapping("/{id:\\d+}/setup-ready")
    public R<AdminProjectStartRequestVO> markSetupReady(@PathVariable Long id,
                                                        @Valid @RequestBody(required = false) ProjectSetupReadyRequest req) {
        return R.ok(startRequestService.markSetupReady(id, req));
    }
}
