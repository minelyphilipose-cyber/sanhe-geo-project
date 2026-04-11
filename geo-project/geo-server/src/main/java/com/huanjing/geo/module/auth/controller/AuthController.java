package com.huanjing.geo.module.auth.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.common.util.SecurityUtils;
import com.huanjing.geo.module.auth.dto.LoginRequest;
import com.huanjing.geo.module.auth.dto.LoginResponse;
import com.huanjing.geo.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Login")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return R.ok(authService.login(req));
    }

    @Operation(summary = "Refresh token")
    @PostMapping("/refresh")
    public R<Map<String, String>> refresh(@RequestBody(required = false) Map<String, String> body) {
        String refreshToken = body == null ? null : body.get("refreshToken");
        String newAccessToken = authService.refresh(refreshToken);
        return R.ok(Map.of("accessToken", newAccessToken));
    }

    @Operation(summary = "Logout")
    @PostMapping("/logout")
    public R<Void> logout() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            authService.logout(userId);
        }
        return R.ok();
    }
}
