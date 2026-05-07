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
        boolean dbSucceeded = false;
        try {
            auditLogMapper.insert(auditLog);
            dbSucceeded = true;
        } catch (Exception ex) {
            auditMetrics.incrementDbFailure();
            log.warn("audit db write failed eventId={} eventType={}", event.getEventId(), event.getEventType(), ex);
        }
        try {
            auditFileLogger.write(event, detailJson);
        } catch (Throwable ex) {
            auditMetrics.incrementFileFailure();
            if (dbSucceeded) {
                log.warn("audit file write failed but db write succeeded eventId={} eventType={}",
                        event.getEventId(), event.getEventType(), ex);
            } else {
                auditMetrics.incrementLost();
                log.error("AUDIT LOST: both db and file write failed eventId={} eventType={}",
                        event.getEventId(), event.getEventType(), ex);
            }
        }
    }
}
