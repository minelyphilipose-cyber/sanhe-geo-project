package com.huanjing.geo.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class TrustedProxyClientIp {
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private TrustedProxyClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String trustedProxyValue = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(trustedProxyValue)
                && IP_LITERAL.matcher(trustedProxyValue.trim()).matches()) {
            return trustedProxyValue.trim();
        }
        return request.getRemoteAddr();
    }
}
