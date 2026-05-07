package com.huanjing.geo.module.content.credential.audit;

import com.huanjing.geo.module.audit.ActorType;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CredentialAuditService implements CredentialAuditHook {

    private static final String TARGET_TYPE = "SELF_MEDIA_COOKIE_CREDENTIAL";

    private final AuditService auditService;

    @Override
    public void onCredentialStored(CookieCredentialMeta meta) {
        AuditEvent event = base("CREDENTIAL_REFRESH", meta, null);
        event.setMode(AuditMode.ASYNC);
        event.setResult(AuditResult.SUCCESS);
        event.setDetail(Map.of("version", meta.version()));
        auditService.record(event);
    }

    @Override
    public void onCredentialDecrypted(CookieCredentialMeta meta, Long operatorId) {
        AuditEvent event = base("CREDENTIAL_DECRYPT", meta, operatorId);
        event.setMode(AuditMode.SYNC);
        event.setSensitive(true);
        event.setResult(AuditResult.SUCCESS);
        event.setDetail(Map.of("version", meta.version()));
        auditService.record(event);
    }

    @Override
    public void onCredentialDestroyed(Long selfMediaAccountId, Long operatorId, int affectedRows) {
        AuditEvent event = new AuditEvent();
        event.setEventType("CREDENTIAL_DESTROY");
        event.setActorType(operatorId == null ? ActorType.SYSTEM : ActorType.OPERATOR);
        event.setActorId(operatorId);
        event.setAccountId(selfMediaAccountId);
        event.setTargetType(TARGET_TYPE);
        event.setTargetId(String.valueOf(selfMediaAccountId));
        event.setMode(AuditMode.SYNC);
        event.setSensitive(true);
        event.setResult(affectedRows > 0 ? AuditResult.SUCCESS : AuditResult.NO_OP);
        event.setDetail(Map.of("affectedRows", affectedRows));
        auditService.record(event);
    }

    @Override
    public void onCredentialAccessDenied(
            Long selfMediaAccountId,
            Long expectedBrandId,
            Long actualBrandId,
            Long operatorId,
            String reason
    ) {
        AuditEvent event = new AuditEvent();
        event.setEventType("CREDENTIAL_DECRYPT");
        event.setActorType(operatorId == null ? ActorType.UNAUTHENTICATED : ActorType.OPERATOR);
        event.setActorId(operatorId);
        event.setBrandId(actualBrandId);
        event.setAccountId(selfMediaAccountId);
        event.setTargetType(TARGET_TYPE);
        event.setTargetId(String.valueOf(selfMediaAccountId));
        event.setMode(AuditMode.SYNC);
        event.setSensitive(true);
        event.setResult(AuditResult.DENIED);
        event.setDetail(Map.of(
                "reason", reason,
                "expectedBrandId", expectedBrandId,
                "actualBrandId", actualBrandId
        ));
        auditService.record(event);
    }

    private AuditEvent base(String eventType, CookieCredentialMeta meta, Long operatorId) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setActorType(operatorId == null ? ActorType.SYSTEM : ActorType.OPERATOR);
        event.setActorId(operatorId);
        event.setBrandId(meta.brandId());
        event.setAccountId(meta.selfMediaAccountId());
        event.setTargetType(TARGET_TYPE);
        event.setTargetId(String.valueOf(meta.id()));
        return event;
    }
}
