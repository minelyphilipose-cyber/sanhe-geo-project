package com.huanjing.geo.module.system.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "System")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;

    @Operation(summary = "Health check")
    @GetMapping("/health")
    public R<Map<String, String>> health() {
        return R.ok(Map.of("status", "UP", "service", "geo-server"));
    }

    @Operation(summary = "Current user profile with permissions")
    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        SysUser user = currentUserService.requireCurrentUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", user.getId());
        payload.put("username", user.getUsername());
        payload.put("displayName", user.getDisplayName());
        payload.put("role", user.getRole());
        payload.put("partnerId", user.getPartnerId());
        payload.put("isActive", user.getIsActive());
        payload.put("permissions", permissionService.listPermKeys(user));
        return R.ok(payload);
    }
}
