package com.huanjing.geo.module.project.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.partner.dto.PartnerProjectStartRequestVO;
import com.huanjing.geo.module.project.dto.ProjectStartRequestSubmitRequest;
import com.huanjing.geo.module.project.service.ProjectStartRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/partner/projects")
@RequiredArgsConstructor
public class PartnerProjectStartRequestController {

    private final ProjectStartRequestService startRequestService;

    @PostMapping("/{id:\\d+}/start-requests")
    public R<PartnerProjectStartRequestVO> submit(@PathVariable Long id,
                                                  @Valid @RequestBody(required = false) ProjectStartRequestSubmitRequest req) {
        return R.ok(startRequestService.submit(id, req));
    }

    @PostMapping("/{id:\\d+}/start-requests/{requestId:\\d+}/cancel")
    public R<PartnerProjectStartRequestVO> cancel(@PathVariable Long id, @PathVariable Long requestId) {
        return R.ok(startRequestService.cancel(id, requestId));
    }
}
