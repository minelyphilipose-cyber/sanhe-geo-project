package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.SelfMediaScheduleCapabilityUpsertRequest;
import com.huanjing.geo.module.content.service.SelfMediaScheduleCapabilityService;
import com.huanjing.geo.module.content.vo.SelfMediaScheduleCapabilityVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "SelfMediaScheduleCapability")
@RestController
@RequestMapping("/api/content/self-media-schedule-capabilities")
@RequiredArgsConstructor
public class SelfMediaScheduleCapabilityController {
    private final SelfMediaScheduleCapabilityService capabilityService;

    @GetMapping
    public R<List<SelfMediaScheduleCapabilityVO>> list() {
        return R.ok(capabilityService.list());
    }

    @GetMapping("/{platform}")
    public R<SelfMediaScheduleCapabilityVO> detail(@PathVariable String platform) {
        return R.ok(capabilityService.detail(platform));
    }

    @PutMapping("/{platform}")
    public R<SelfMediaScheduleCapabilityVO> upsert(@PathVariable String platform,
                                                   @Valid @RequestBody SelfMediaScheduleCapabilityUpsertRequest request) {
        request.setPlatform(platform);
        return R.ok(capabilityService.upsert(request));
    }
}
