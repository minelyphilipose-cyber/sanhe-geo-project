package com.huanjing.geo.common.llm.monitoring;

import com.huanjing.geo.common.llm.monitoring.dto.HunyuanCapacityVO;
import com.huanjing.geo.common.llm.monitoring.dto.LlmRuntimeConfigVO;
import com.huanjing.geo.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/llm-capacity")
@RequiredArgsConstructor
public class LlmCapacityMonitoringController {
    private final LlmCapacityMonitoringService monitoringService;

    @GetMapping("/runtime-config")
    public R<LlmRuntimeConfigVO> runtimeConfig() {
        return R.ok(monitoringService.runtimeConfig());
    }

    @GetMapping("/hunyuan")
    public R<HunyuanCapacityVO> hunyuan() {
        return R.ok(monitoringService.hunyuanCapacity());
    }
}
