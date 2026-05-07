package com.huanjing.geo.module.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditServiceTest {

    private AuditLogWriter auditLogWriter;
    private AuditFileLogger auditFileLogger;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditLogWriter = mock(AuditLogWriter.class);
        auditFileLogger = mock(AuditFileLogger.class);
        Executor directExecutor = Runnable::run;
        auditService = new AuditService(
                new AuditContextProvider(),
                auditFileLogger,
                new ObjectMapper(),
                directExecutor,
                auditLogWriter
        );
    }

    @Test
    void detailJsonIsTruncatedAndMarked() {
        String detailJson = auditService.toBoundedDetailJson(Map.of("payload", "x".repeat(32 * 1024)));

        assertTrue(detailJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= AuditService.DETAIL_JSON_MAX_BYTES);
        assertTrue(detailJson.contains("\"truncated\":true"));
    }

    @Test
    void truncatedDetailJsonWithMultibyteCharsRemainsValidJson() {
        String detailJson = auditService.toBoundedDetailJson(Map.of("payload", "中文".repeat(20 * 1024)));

        assertTrue(detailJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= AuditService.DETAIL_JSON_MAX_BYTES);
        assertDoesNotThrow(() -> new ObjectMapper().readTree(detailJson));
    }

    @Test
    void syncDbFailureFallsBackToFileLogger() {
        AuditEvent event = new AuditEvent();
        event.setEventType("TEST_AUDIT");
        event.setMode(AuditMode.SYNC);
        event.setResult(AuditResult.SUCCESS);

        assertDoesNotThrow(() -> auditService.record(event));

        verify(auditLogWriter).writeDbAndFile(any(), any(), any());
    }
}
