package com.huanjing.geo.module.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.dto.*;
import com.huanjing.geo.module.system.service.UserAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "UserAdmin")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping("/users")
    public R<Page<UserAdminVO>> userPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleKey,
            @RequestParam(required = false) String accountScope,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) Boolean isActive
    ) {
        return R.ok(userAdminService.page(current, size, keyword, roleKey, accountScope, partnerId, isActive));
    }

    @GetMapping("/users/{id}")
    public R<UserAdminVO> userDetail(@PathVariable Long id) {
        return R.ok(userAdminService.detail(id));
    }

    @PostMapping("/users")
    public R<UserAdminVO> createUser(@Valid @RequestBody UserCreateRequest req) {
        return R.ok(userAdminService.create(req));
    }

    @PutMapping("/users/{id}")
    public R<UserAdminVO> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req) {
        return R.ok(userAdminService.update(id, req));
    }

    @PutMapping("/users/{id}/status")
    public R<Void> updateUserStatus(@PathVariable Long id, @Valid @RequestBody UserStatusUpdateRequest req) {
        userAdminService.updateStatus(id, req.getIsActive());
        return R.ok();
    }

    @PostMapping("/users/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        userAdminService.resetPassword(id, req.getNewPassword());
        return R.ok();
    }

    @PutMapping("/users/{id}/role")
    public R<UserAdminVO> bindRole(@PathVariable Long id, @Valid @RequestBody UserRoleBindRequest req) {
        return R.ok(userAdminService.bindRole(id, req.getRoleKey()));
    }

    @GetMapping("/roles")
    public R<List<RoleSimpleVO>> roleOptions() {
        return R.ok(userAdminService.roleOptions());
    }
}
