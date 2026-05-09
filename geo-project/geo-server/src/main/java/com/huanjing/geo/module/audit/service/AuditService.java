package com.huanjing.geo.module.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.entity.AuditLog;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class AuditService {

    static final int DETAIL_JSON_MAX_BYTES = 16 * 1024;

    private final AuditContextProvider contextProvider;
    private final AuditFileLogger auditFileLogger;
    private final ObjectMapper objectMapper;
    private final Executor auditExecutor;
    private final AuditLogWriter auditLogWriter;

    public AuditService(
            AuditContextProvider contextProvider,
            AuditFileLogger auditFileLogger,
            ObjectMapper objectMapper,
            @Qualifier("auditExecutor") Executor auditExecutor,
            AuditLogWriter auditLogWriter
    ) {
        this.contextProvider = contextProvider;
        this.auditFileLogger = auditFileLogger;
        this.objectMapper = objectMapper;
        this.auditExecutor = auditExecutor;
        this.auditLogWriter = auditLogWriter;
    }

    public void record(AuditEvent event) {
        // Request/security/MDC context is materialized here on the caller thread. Async workers
        // only persist the already-filled event and must not depend on request thread-locals.
        contextProvider.fillDefaults(event);
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(LocalDateTime.now());
        }
        if (event.getMode() == null) {
            event.setMode(AuditMode.ASYNC);
        }
        String detailJson = toBoundedDetailJson(event.getDetail());
        if (event.getMode() == AuditMode.FILE_ONLY) {
            auditFileLogger.write(event, detailJson);
            return;
        }
        AuditLog auditLog = toEntity(event, detailJson);
        if (event.getMode() == AuditMode.SYNC) {
            if (isTransactionSynchronizationAvailable()) {
                auditLogWriter.writeFileOnly(event, detailJson);
                runAfterCommit(() -> auditLogWriter.writeDbOnly(event, auditLog));
                return;
            }
            auditLogWriter.writeDbAndFile(event, auditLog, detailJson);
            return;
        }
        if (isTransactionSynchronizationAvailable()) {
            runAfterCommit(() -> auditExecutor.execute(() -> auditLogWriter.writeDbAndFile(event, auditLog, detailJson)));
            return;
        }
        auditExecutor.execute(() -> auditLogWriter.writeDbAndFile(event, auditLog, detailJson));
    }

    private boolean isTransactionSynchronizationAvailable() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive();
    }

    private void runAfterCommit(Runnable task) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    String toBoundedDetailJson(Map<String, Object> detail) {
        if (detail == null) {
            return null;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            json = "{\"serializationError\":\"" + escape(ex.getMessage()) + "\"}";
        }
        if (json.getBytes(StandardCharsets.UTF_8).length <= DETAIL_JSON_MAX_BYTES) {
            return json;
        }
        Map<String, Object> truncated = new LinkedHashMap<>();
        truncated.put("truncated", true);
        truncated.put("originalBytes", json.getBytes(StandardCharsets.UTF_8).length);
        truncated.put("preview", truncateUtf8(json, 12 * 1024));
        try {
            String bounded = objectMapper.writeValueAsString(truncated);
            while (bounded.getBytes(StandardCharsets.UTF_8).length > DETAIL_JSON_MAX_BYTES) {
                truncated.put("preview", truncateUtf8((String) truncated.get("preview"), 1024));
                bounded = objectMapper.writeValueAsString(truncated);
            }
            return bounded;
        } catch (JsonProcessingException ex) {
            return "{\"truncated\":true}";
        }
    }

    private AuditLog toEntity(AuditEvent event, String detailJson) {
        AuditLog log = new AuditLog();
        log.setEventId(event.getEventId());
        log.setEventType(event.getEventType());
        log.setActorType(event.getActorType() == null ? null : event.getActorType().name());
        log.setActorId(event.getActorId());
        log.setBrandId(event.getBrandId());
        log.setAccountId(event.getAccountId());
        log.setTaskId(event.getTaskId());
        log.setTargetType(event.getTargetType());
        log.setTargetId(event.getTargetId());
        log.setResult(event.getResult() == null ? null : event.getResult().name());
        log.setSensitive(event.isSensitive());
        log.setMode(event.getMode() == null ? null : event.getMode().name());
        log.setIpAddress(event.getIpAddress());
        log.setUserAgent(event.getUserAgent());
        log.setExtensionSessionId(event.getExtensionSessionId());
        log.setDeviceFingerprintHash(event.getDeviceFingerprintHash());
        log.setRequestId(event.getRequestId());
        log.setTraceId(event.getTraceId());
        log.setDetailJson(detailJson);
        log.setErrorCode(event.getErrorCode());
        log.setErrorMessage(truncateText(event.getErrorMessage(), 512));
        log.setCreatedAt(event.getCreatedAt());
        return log;
    }

    private String truncateUtf8(String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        int safeEnd = maxBytes;
        while (safeEnd > 0 && (bytes[safeEnd] & 0xC0) == 0x80) {
            safeEnd--;
        }
        return new String(Arrays.copyOf(bytes, safeEnd), StandardCharsets.UTF_8);
    }

    private String truncateText(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        if (maxChars <= 3) {
            return value.substring(0, maxChars);
        }
        return value.substring(0, maxChars - 3) + "...";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
