package com.huanjing.geo.module.audit.service;

import com.huanjing.geo.common.util.SecurityUtils;
import com.huanjing.geo.module.audit.ActorType;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
@Slf4j
public class AuditContextProvider {

    public void fillDefaults(AuditEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(newEventId());
        }
        if (event.getActorId() == null) {
            try {
                event.setActorId(SecurityUtils.getCurrentUserId());
            } catch (Exception ex) {
                log.debug("get current user id failed while filling audit context", ex);
            }
        }
        if (event.getActorType() == null) {
            event.setActorType(event.getActorId() == null ? ActorType.UNAUTHENTICATED : ActorType.OPERATOR);
        }
        if (event.getTraceId() == null) {
            event.setTraceId(firstText(MDC.get("traceId"), MDC.get("trace_id")));
        }
        if (event.getRequestId() == null) {
            event.setRequestId(firstText(MDC.get("requestId"), MDC.get("request_id")));
        }

        HttpServletRequest request = currentRequest();
        if (request != null) {
            if (event.getIpAddress() == null) {
                event.setIpAddress(clientIp(request));
            }
            if (event.getUserAgent() == null) {
                event.setUserAgent(request.getHeader("User-Agent"));
            }
            if (event.getRequestId() == null) {
                event.setRequestId(firstText(request.getHeader("X-Request-Id"), request.getHeader("X-Trace-Id")));
            }
        }
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : null;
    }
}
