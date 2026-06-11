package com.huanjing.geo.module.retention.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.presale.dto.DataRetentionSlimDryRunRequest;
import com.huanjing.geo.module.presale.dto.DataRetentionSlimDryRunResponse;
import com.huanjing.geo.module.retention.service.DataRetentionSlimDryRunService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DataRetention")
@RestController
@RequestMapping("/api/data-retention")
@RequiredArgsConstructor
public class DataRetentionController {

    private final DataRetentionSlimDryRunService dataRetentionSlimDryRunService;

    @PostMapping("/slim/dry-run")
    public R<DataRetentionSlimDryRunResponse> slimDryRun(@RequestBody DataRetentionSlimDryRunRequest request) {
        return R.ok(dataRetentionSlimDryRunService.dryRun(request == null ? new DataRetentionSlimDryRunRequest() : request));
    }
}
