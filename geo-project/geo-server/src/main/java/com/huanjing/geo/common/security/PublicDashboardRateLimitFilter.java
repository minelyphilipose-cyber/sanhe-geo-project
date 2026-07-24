package com.huanjing.geo.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.result.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicDashboardRateLimitFilter extends OncePerRequestFilter {

    private static final List<String> PATH_PREFIXES = List.of(
            "/api/public/dashboard/",
            "/api/public/mobile-dashboard/"
    );
    private static final int LIMIT_PER_MINUTE = 45;
    private static final int WECHAT_JS_SDK_LIMIT_PER_MINUTE = 120;
    private static final DateTimeFormatter WINDOW_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DefaultRedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local current = tonumber(redis.call('GET', KEYS[1]) or '0') " +
                    "local limit = tonumber(ARGV[1]) " +
                    "if current >= limit then return 0 end " +
                    "current = redis.call('INCR', KEYS[1]) " +
                    "if current == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) end " +
                    "if current > limit then return 0 end " +
                    "return 1",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return PATH_PREFIXES.stream().noneMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        String minuteWindow = LocalDateTime.now().format(WINDOW_FMT);
        boolean wechatJsSdkPath = request.getRequestURI().startsWith("/api/public/mobile-dashboard/wechat-js-sdk/");
        int limit = wechatJsSdkPath ? WECHAT_JS_SDK_LIMIT_PER_MINUTE : LIMIT_PER_MINUTE;
        String operation = wechatJsSdkPath ? "wechat-js-sdk" : "default";
        String key = "geo:public:dashboard:rate:" + operation + ":" + clientIp + ":" + minuteWindow;

        Long result = stringRedisTemplate.execute(
                LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(limit),
                "90"
        );
        if (result != null && result > 0) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Public dashboard rate limit exceeded path={} ip={}", request.getRequestURI(), clientIp);
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(429, "操作过于频繁，请稍后再试")));
    }

    private String resolveClientIp(HttpServletRequest request) {
        return TrustedProxyClientIp.resolve(request);
    }
}
