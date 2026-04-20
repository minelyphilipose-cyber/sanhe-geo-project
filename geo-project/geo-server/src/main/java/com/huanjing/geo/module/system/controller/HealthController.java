package com.huanjing.geo.module.system.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.dto.CurrentUserPasswordChangeRequest;
import com.huanjing.geo.module.system.dto.CurrentUserProfileUpdateRequest;
import com.huanjing.geo.module.system.dto.CurrentUserProfileVO;
import com.huanjing.geo.module.system.service.CurrentUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "System")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final CurrentUserProfileService currentUserProfileService;

    @Operation(summary = "Health check")
    @GetMapping("/health")
    public R<Map<String, String>> health() {
        return R.ok(Map.of("status", "UP", "service", "geo-server"));
    }

    @Operation(summary = "Current user profile with permissions")
    @GetMapping("/me")
    public R<CurrentUserProfileVO> me() {
        return R.ok(currentUserProfileService.me());
    }

    @Operation(summary = "Update current user profile")
    @PutMapping("/me/profile")
    public R<CurrentUserProfileVO> updateProfile(@Valid @RequestBody CurrentUserProfileUpdateRequest req) {
        return R.ok(currentUserProfileService.updateProfile(req));
    }

    @Operation(summary = "Upload current user avatar")
    @PostMapping("/me/avatar")
    public R<CurrentUserProfileVO> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return R.ok(currentUserProfileService.uploadAvatar(file));
    }

    @Operation(summary = "Change current user password")
    @PutMapping("/me/password")
    public R<Void> changePassword(@Valid @RequestBody CurrentUserPasswordChangeRequest req) {
        currentUserProfileService.changePassword(req);
        return R.ok();
    }
}
