package com.huanjing.geo.module.audit.service;

import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.entity.AuditLog;
import com.huanjing.geo.module.audit.mapper.AuditLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogWriterTest {

    private AuditLogMapper auditLogMapper;
    private AuditFileLogger auditFileLogger;
    private AuditMetrics auditMetrics;
    private AuditLogWriter auditLogWriter;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(AuditLogMapper.class);
        auditFileLogger = mock(AuditFileLogger.class);
        auditMetrics = mock(AuditMetrics.class);
        auditLogWriter = new AuditLogWriter(auditLogMapper, auditFileLogger, auditMetrics);
    }

    @Test
    void dbFailureFallsBackToFileLogger() {
        doThrow(new RuntimeException("db down")).when(auditLogMapper).insert(any());
        AuditEvent event = new AuditEvent();
        event.setEventId("event-1");
        event.setEventType("TEST_AUDIT");
        event.setResult(AuditResult.SUCCESS);

        assertDoesNotThrow(() -> auditLogWriter.writeDbAndFile(event, new AuditLog(), "{}"));

        verify(auditFileLogger).write(event, "{}");
        verify(auditMetrics).incrementDbFailure();
    }

    @Test
    void bothDbAndFileFailureIncrementsLostCounter() {
        doThrow(new RuntimeException("db down")).when(auditLogMapper).insert(any());
        doThrow(new RuntimeException("disk full")).when(auditFileLogger).write(any(), any());
        AuditEvent event = new AuditEvent();
        event.setEventId("event-2");
        event.setEventType("TEST_AUDIT");

        assertDoesNotThrow(() -> auditLogWriter.writeDbAndFile(event, new AuditLog(), "{}"));

        verify(auditMetrics).incrementDbFailure();
        verify(auditMetrics).incrementFileFailure();
        verify(auditMetrics).incrementLost();
    }
}
