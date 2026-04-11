package com.huanjing.geo.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户信息 (存在 SecurityContext 中)
 */
@Data
@AllArgsConstructor
public class LoginUser {
    private Long userId;
    private String username;
    private String role;
}
