package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.module.audit.ActorType;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
class ExtensionAuditSupport {

    private final AuditService auditService;

    void record(
            String eventType,
            AuditResult result,
            AuditMode mode,
            boolean sensitive,
            Long operatorId,
            Long brandId,
            Long accountId,
            Long taskId,
            Long extensionSessionId,
            String targetType,
            String targetId,
            String errorCode,
            String errorMessage,
            Map<String, Object> detail
    ) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setActorType(operatorId == null ? ActorType.UNAUTHENTICATED : ActorType.OPERATOR);
        event.setActorId(operatorId);
        event.setBrandId(brandId);
        event.setAccountId(accountId);
        event.setTaskId(taskId);
        event.setExtensionSessionId(extensionSessionId);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setResult(result);
        event.setMode(mode);
        event.setSensitive(sensitive);
        event.setErrorCode(errorCode);
        event.setErrorMessage(errorMessage);
        event.setDetail(detail == null ? null : new LinkedHashMap<>(detail));
        auditService.record(event);
    }
}
