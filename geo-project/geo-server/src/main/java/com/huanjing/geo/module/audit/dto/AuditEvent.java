package com.huanjing.geo.module.audit.dto;

import com.huanjing.geo.module.audit.ActorType;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AuditEvent {
    private String eventId;
    private String eventType;
    private ActorType actorType;
    private Long actorId;
    private Long brandId;
    private Long accountId;
    private Long taskId;
    private String targetType;
    private String targetId;
    private AuditResult result;
    private boolean sensitive;
    private AuditMode mode;
    private String ipAddress;
    private String userAgent;
    private Long extensionSessionId;
    private String deviceFingerprintHash;
    private String requestId;
    private String traceId;
    private Map<String, Object> detail;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
}
