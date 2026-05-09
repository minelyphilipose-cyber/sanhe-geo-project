package com.huanjing.geo.module.audit.service;

import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.entity.AuditLog;
import com.huanjing.geo.module.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogWriter {

    private final AuditLogMapper auditLogMapper;
    private final AuditFileLogger auditFileLogger;
    private final AuditMetrics auditMetrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeDbAndFile(AuditEvent event, AuditLog auditLog, String detailJson) {
        boolean dbSucceeded = writeDb(event, auditLog);
        boolean fileSucceeded = writeFile(event, detailJson);
        if (!dbSucceeded && !fileSucceeded) {
            auditMetrics.incrementLost();
            log.error("AUDIT LOST: both db and file write failed eventId={} eventType={}",
                    event.getEventId(), event.getEventType());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean writeDbOnly(AuditEvent event, AuditLog auditLog) {
        return writeDb(event, auditLog);
    }

    public boolean writeFileOnly(AuditEvent event, String detailJson) {
        return writeFile(event, detailJson);
    }

    private boolean writeDb(AuditEvent event, AuditLog auditLog) {
        try {
            auditLogMapper.insert(auditLog);
            return true;
        } catch (Exception ex) {
            auditMetrics.incrementDbFailure();
            log.warn("audit db write failed eventId={} eventType={}", event.getEventId(), event.getEventType(), ex);
            return false;
        }
    }

    private boolean writeFile(AuditEvent event, String detailJson) {
        try {
            auditFileLogger.write(event, detailJson);
            return true;
        } catch (Throwable ex) {
            auditMetrics.incrementFileFailure();
            log.warn("audit file write failed eventId={} eventType={}",
                    event.getEventId(), event.getEventType(), ex);
            return false;
        }
    }
}
