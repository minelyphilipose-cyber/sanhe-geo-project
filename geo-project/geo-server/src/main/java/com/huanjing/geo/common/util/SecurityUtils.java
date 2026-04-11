package com.huanjing.geo.common.util;

import com.huanjing.geo.common.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            return (LoginUser) auth.getPrincipal();
        }
        return null;
    }

    public static Long getCurrentUserId() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    public static String getCurrentRole() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getRole() : null;
    }

    public static Integer getCurrentTokenVersion() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getTokenVersion() : null;
    }
}
