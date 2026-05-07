package com.huanjing.geo.module.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuditFileLogger {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("security.audit");

    private final ObjectMapper objectMapper;

    public void write(AuditEvent event, String detailJson) {
        try {
            AUDIT_LOGGER.info(objectMapper.writeValueAsString(payload(event, detailJson)));
        } catch (JsonProcessingException ex) {
            safeWriteFallback(event, ex);
        } catch (Throwable throwable) {
            System.err.println("AUDIT FILE WRITE FAILED: eventId=" + event.getEventId()
                    + " eventType=" + event.getEventType()
                    + " error=" + throwable.getMessage());
        }
    }

    private Map<String, Object> payload(AuditEvent event, String detailJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getEventId());
        payload.put("eventType", event.getEventType());
        payload.put("actorType", event.getActorType() == null ? null : event.getActorType().name());
        payload.put("actorId", event.getActorId());
        payload.put("brandId", event.getBrandId());
        payload.put("accountId", event.getAccountId());
        payload.put("taskId", event.getTaskId());
        payload.put("targetType", event.getTargetType());
        payload.put("targetId", event.getTargetId());
        payload.put("result", event.getResult() == null ? null : event.getResult().name());
        payload.put("sensitive", event.isSensitive());
        payload.put("mode", event.getMode() == null ? null : event.getMode().name());
        payload.put("ipAddress", event.getIpAddress());
        payload.put("userAgent", event.getUserAgent());
        payload.put("extensionSessionId", event.getExtensionSessionId());
        payload.put("requestId", event.getRequestId());
        payload.put("traceId", event.getTraceId());
        payload.put("detailJson", detailJson);
        payload.put("errorCode", event.getErrorCode());
        payload.put("errorMessage", event.getErrorMessage());
        payload.put("createdAt", event.getCreatedAt());
        return payload;
    }

    private void safeWriteFallback(AuditEvent event, JsonProcessingException ex) {
        try {
            AUDIT_LOGGER.info("eventId={} eventType={} result={} audit_file_json_failed={}",
                    event.getEventId(), event.getEventType(), event.getResult(), ex.getMessage());
        } catch (Throwable throwable) {
            System.err.println("AUDIT FILE WRITE FAILED: eventId=" + event.getEventId()
                    + " eventType=" + event.getEventType()
                    + " error=" + throwable.getMessage());
        }
    }
}
