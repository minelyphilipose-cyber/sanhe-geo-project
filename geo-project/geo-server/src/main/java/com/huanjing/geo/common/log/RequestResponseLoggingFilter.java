package com.huanjing.geo.common.log;

import com.huanjing.geo.common.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger HTTP_LOG = LoggerFactory.getLogger("http.access");
    private static final String TRACE_ID_KEY = "traceId";
    private static final String USER_ID_KEY = "userId";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/webjars")
                || uri.startsWith("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID_KEY, traceId);

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        responseWrapper.setHeader("X-Trace-Id", traceId);

        Throwable throwable = null;
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Throwable ex) {
            throwable = ex;
            if (ex instanceof ServletException servletException) {
                throw servletException;
            }
            if (ex instanceof IOException ioException) {
                throw ioException;
            }
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (ex instanceof Error error) {
                throw error;
            }
            throw new ServletException(ex);
        } finally {
            try {
                logRequestAndResponse(requestWrapper, responseWrapper, throwable, start);
            } catch (Exception ex) {
                log.warn("request/response logging failed: {}", ex.getMessage());
            }
            responseWrapper.copyBodyToResponse();
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(USER_ID_KEY);
        }
    }

    private void logRequestAndResponse(ContentCachingRequestWrapper request,
                                       ContentCachingResponseWrapper response,
                                       Throwable throwable,
                                       long startTime) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullPath = StringUtils.hasText(queryString) ? uri + "?" + queryString : uri;
        String clientIp = clientIp(request);
        String mdcUserId = MDC.get(USER_ID_KEY);
        Long userId = StringUtils.hasText(mdcUserId) ? parseLongSafe(mdcUserId) : SecurityUtils.getCurrentUserId();
        long costMs = System.currentTimeMillis() - startTime;

        Map<String, String> headers = HttpLogSanitizer.maskHeaders(readHeaders(request));
        boolean suppressBody = shouldSuppressBody(uri);
        String requestBody = suppressBody ? "[suppressed]" : readRequestBody(request);
        String responseBody = suppressBody ? "[suppressed]" : readResponseBody(response);
        int status = response.getStatus();

        if (shouldSuppressNoopLocalAgentPoll(uri, status, throwable, responseBody)) {
            HTTP_LOG.debug("http_poll_noop traceId={} userId={} method={} path={} status={} costMs={}",
                    mdcTraceId(), userId, method, fullPath, status, costMs);
            return;
        }

        HTTP_LOG.info("http_request traceId={} userId={} method={} path={} ip={} headers={} body={}",
                mdcTraceId(), userId, method, fullPath, clientIp, headers, requestBody);
        if (throwable == null) {
            HTTP_LOG.info("http_response traceId={} userId={} method={} path={} status={} costMs={} body={}",
                    mdcTraceId(), userId, method, fullPath, status, costMs, responseBody);
        } else {
            HTTP_LOG.error("http_error traceId={} userId={} method={} path={} status={} costMs={} errType={} errMsg={}",
                    mdcTraceId(), userId, method, fullPath, status, costMs,
                    throwable.getClass().getSimpleName(), throwable.getMessage());
        }
    }

    private boolean shouldSuppressNoopLocalAgentPoll(String uri,
                                                     int status,
                                                     Throwable throwable,
                                                     String responseBody) {
        if (throwable != null || status < 200 || status >= 300) {
            return false;
        }
        if (!"/api/v1/local-agent/self-media-schedules/claim-next".equals(uri)
                && !"/api/v1/local-agent/self-media-schedules/publish-checks/claim-next".equals(uri)) {
            return false;
        }
        return responseBody.contains("\"claimBlockedReason\":\"NO_DUE_TASK\"");
    }

    private boolean shouldSuppressBody(String uri) {
        return "/api/content/articles/manual-import/parse".equals(uri);
    }

    private String readRequestBody(ContentCachingRequestWrapper request) {
        if (!isTextBased(request.getContentType())) {
            return "";
        }
        byte[] bytes = request.getContentAsByteArray();
        if (bytes.length == 0) {
            return "";
        }
        return HttpLogSanitizer.maskBody(new String(bytes, StandardCharsets.UTF_8), request.getContentType());
    }

    private String readResponseBody(ContentCachingResponseWrapper response) {
        if (!isTextBased(response.getContentType())) {
            return "";
        }
        byte[] bytes = response.getContentAsByteArray();
        if (bytes.length == 0) {
            return "";
        }
        return HttpLogSanitizer.maskBody(new String(bytes, StandardCharsets.UTF_8), response.getContentType());
    }

    private boolean isTextBased(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        String lower = contentType.toLowerCase();
        return lower.contains(MediaType.APPLICATION_JSON_VALUE)
                || lower.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                || lower.contains(MediaType.TEXT_PLAIN_VALUE)
                || lower.contains(MediaType.TEXT_HTML_VALUE)
                || lower.contains("xml");
    }

    private Map<String, String> readHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (StringUtils.hasText(traceId)) {
            return traceId.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String mdcTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    private String clientIp(HttpServletRequest request) {
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

    private Long parseLongSafe(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
