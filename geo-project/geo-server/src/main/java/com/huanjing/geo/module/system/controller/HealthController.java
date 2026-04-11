package com.huanjing.geo.module.system.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.common.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "系统")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(summary = "健康检查(公开)")
    @GetMapping("/health")
    public R<Map<String, String>> health() {
        return R.ok(Map.of("status", "UP", "service", "geo-server"));
    }

    @Operation(summary = "当前用户信息(需登录)")
    @GetMapping("/me")
    public R<Object> me() {
        return R.ok(SecurityUtils.getCurrentUser());
    }
}
