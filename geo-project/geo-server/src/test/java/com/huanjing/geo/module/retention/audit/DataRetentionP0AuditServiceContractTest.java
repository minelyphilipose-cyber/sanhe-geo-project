package com.huanjing.geo.module.retention.audit;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRetentionP0AuditServiceContractTest {

    @Test
    void p0WriterKeepsVersionOutOfCompatibilityAndMarkersMandatory() throws Exception {
        String source = source("com/huanjing/geo/module/retention/audit/service/DataRetentionP0AuditService.java");
        assertTrue(source.contains("runCreatedByVersion"));
        assertTrue(source.contains("currentWriterVersion"));
        assertTrue(source.contains("@Transactional(propagation = Propagation.MANDATORY)"));
        assertTrue(source.contains("isActualTransactionActive"));
        assertTrue(source.contains("isCurrentTransactionReadOnly"));
        assertTrue(source.contains("outcome_status = 'pending' AND detail_purged_at IS NULL"));
        assertTrue(source.contains("rolled_back' AND detail_purged_at IS NULL"));
        assertTrue(source.contains("fingerprintFromEffectiveConfig"));
        assertTrue(source.contains("recoveredAttempt.matches(firstAttempt)"));
        assertTrue(source.contains("originScopeMatches"));
        assertTrue(source.contains("requestedEventTime == null ? attempt.finishedAt()"));
        assertTrue(source.contains("aggregateBusinessCounts"));
        assertTrue(source.contains("retentionWindowStart, command.retentionWindowStart()"));
        assertTrue(source.contains("retryReplayMatches"));
        assertTrue(source.contains("existing audit-only run is not a complete replay triple"));
        assertTrue(source.contains("OperationReplayStatus.TERMINAL_REPLAY"));
        assertFalse(source.contains("application_version = ? AND"));
    }

    @Test
    void legacyFacadeRemainsIndependentAndExecuteSettingsAreUntouched() throws Exception {
        String legacy = source("com/huanjing/geo/module/retention/service/DataRetentionRunAuditService.java");
        String typed = source("com/huanjing/geo/module/retention/audit/service/DataRetentionP0AuditService.java");
        assertFalse(typed.contains("DataRetentionRunAuditService"));
        assertTrue(legacy.contains("public Long startRun(String domain"));
        assertTrue(legacy.contains("public void finishRun(Long runId"));
    }

    @Test
    void evidenceWriterDoesNotImplementWp8Canonicalization() throws Exception {
        String codec = source("com/huanjing/geo/module/retention/audit/support/DataRetentionAuditJsonCodec.java");
        assertTrue(codec.contains("STRICT_DUPLICATE_DETECTION"));
        assertTrue(codec.contains("MessageDigest.isEqual"));
        assertTrue(codec.contains("bytes.length > MAX_JSON_BYTES"));
        assertFalse(codec.contains("evidenceSchemaVersion"));
    }

    @Test
    void executeSwitchDefaultsRemainFalseAndRequiredProfileIncludesWp3MysqlTest() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        assertTrue(application.contains("GEO_RETENTION_WEBSITE_PUBLISHED_CLEANUP_EXECUTE_ENABLED:false"));
        assertTrue(application.contains("GEO_RETENTION_POLL_RESULTS_EXECUTE_ENABLED:false"));
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertTrue(pom.contains("DataRetentionP0AuditServiceMysqlIntegrationTest.java"));
        assertTrue(pom.contains("<retention.mysql-it.required>true</retention.mysql-it.required>"));
    }

    private String source(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java").resolve(relativePath), StandardCharsets.UTF_8);
    }
}
