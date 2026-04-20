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
import org.springframework.util.StringUtils;
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

    private static final String PATH_PREFIX = "/api/public/dashboard/";
    private static final int LIMIT_PER_MINUTE = 30;
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
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        String minuteWindow = LocalDateTime.now().format(WINDOW_FMT);
        String key = "geo:public:dashboard:rate:" + clientIp + ":" + minuteWindow;

        Long result = stringRedisTemplate.execute(
                LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(LIMIT_PER_MINUTE),
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
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(429, "Too many requests, please try again later")));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String[] headerNames = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headerNames) {
            String value = request.getHeader(header);
            if (!StringUtils.hasText(value) || "unknown".equalsIgnoreCase(value)) {
                continue;
            }
            int comma = value.indexOf(',');
            return comma > 0 ? value.substring(0, comma).trim() : value.trim();
        }
        return request.getRemoteAddr();
    }
}
