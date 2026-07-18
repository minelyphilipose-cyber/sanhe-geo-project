package com.huanjing.geo.module.system.modeldiagnostic;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticSession;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticRunStatus;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticRunMapper;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticSessionMapper;
import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticHistoryQuery;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelDiagnosticPersistenceContractTest {

    private static final Path V315_MIGRATION = Path.of(
            "src/main/resources/db/migration/V315__ai_model_diagnostic_foundation.sql");
    private static final Path V316_MIGRATION = Path.of(
            "src/main/resources/db/migration/V316__complete_ai_model_diagnostic_contract.sql");

    @Test
    void migrationFreezesOwnershipIdempotencyAndAuditTurnConstraints() throws Exception {
        String sql = completeMigrationContract();

        assertTrue(sql.contains("CREATE TABLE ai_model_diagnostic_sessions"));
        assertTrue(sql.contains("CREATE TABLE ai_model_diagnostic_runs"));
        assertTrue(sql.contains("UNIQUE KEY uk_ai_diag_session_owner_uuid (operator_id, session_id)"));
        assertTrue(sql.contains("UNIQUE KEY uk_ai_diag_session_full_identity (id, operator_id, session_id)"));
        assertTrue(sql.contains("UNIQUE KEY uk_ai_diag_run_owner_client_request (operator_id, client_request_id)"));
        assertTrue(sql.contains("UNIQUE KEY uk_ai_diag_run_session_turn (operator_id, session_id, turn_no)"));
        assertTrue(sql.contains("FOREIGN KEY (session_record_id, operator_id, session_id)"));
        assertTrue(sql.contains("REFERENCES ai_model_diagnostic_sessions(id, operator_id, session_id)"));
        assertTrue(sql.contains("request_fingerprint CHAR(64)"));
        assertTrue(sql.contains("config_snapshot_json JSON NOT NULL"));
        assertTrue(sql.contains("probe_version VARCHAR(32)"));
        assertTrue(sql.contains("template_version VARCHAR(64)"));
        assertTrue(sql.contains("KEY idx_ai_diag_run_cleanup (created_at, id)"));
    }

    @Test
    void migrationSeparatesExecutionStatusFromDiagnosticConclusion() throws Exception {
        String sql = completeMigrationContract();

        assertTrue(sql.contains("status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'REJECTED', 'ABANDONED')"));
        assertTrue(sql.contains("status = 'SUCCEEDED' AND conclusion IS NOT NULL"));
        assertTrue(sql.contains("status = 'FAILED' AND conclusion IS NOT NULL AND conclusion = 'FAIL'"));
        assertTrue(sql.contains("status IN ('RUNNING', 'REJECTED', 'ABANDONED') AND conclusion IS NULL"));
        assertFalse(sql.contains("status IN ('PENDING'"));
    }

    @Test
    void migrationGrantsSinglePermissionOnlyToFrozenRoles() throws Exception {
        String sql = Files.readString(V315_MIGRATION);

        assertTrue(sql.contains("'ai.platform.diagnose'"));
        assertTrue(sql.contains("r.role_key IN ('manager', 'super_admin')"));
        assertFalse(sql.contains("r.role_key IN ('operator'"));
        assertFalse(sql.contains("r.role_key IN ('delivery_manager'"));
        assertFalse(sql.contains("r.role_key IN ('sales'"));
        assertEquals("ai.platform.diagnose", ModelDiagnosticPermissions.DIAGNOSE);
    }

    @Test
    void appliedV315ChecksumRemainsImmutable() throws Exception {
        CRC32 checksum = new CRC32();
        for (String line : Files.readAllLines(V315_MIGRATION, StandardCharsets.UTF_8)) {
            checksum.update(line.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(459344448, (int) checksum.getValue());
        String v315 = Files.readString(V315_MIGRATION);
        String v316 = Files.readString(V316_MIGRATION);
        assertFalse(v315.contains("template_version VARCHAR(64)"));
        assertTrue(v316.contains("ADD COLUMN template_version VARCHAR(64)"));
        assertTrue(v316.contains("DROP FOREIGN KEY fk_ai_diag_run_session"));
    }

    @Test
    void runStateOnlyMovesForwardFromRunning() {
        assertTrue(ModelDiagnosticRunStatus.RUNNING.canTransitionTo(ModelDiagnosticRunStatus.SUCCEEDED));
        assertTrue(ModelDiagnosticRunStatus.RUNNING.canTransitionTo(ModelDiagnosticRunStatus.FAILED));
        assertTrue(ModelDiagnosticRunStatus.RUNNING.canTransitionTo(ModelDiagnosticRunStatus.ABANDONED));
        assertTrue(ModelDiagnosticRunStatus.RUNNING.canTransitionTo(ModelDiagnosticRunStatus.REJECTED));
        assertFalse(ModelDiagnosticRunStatus.SUCCEEDED.canTransitionTo(ModelDiagnosticRunStatus.RUNNING));
        assertTrue(ModelDiagnosticRunStatus.REJECTED.terminal());
    }

    @Test
    void entitiesMapToDedicatedDiagnosticTablesAndVersionSnapshots() throws Exception {
        assertEquals("ai_model_diagnostic_sessions",
                AiModelDiagnosticSession.class.getAnnotation(TableName.class).value());
        assertEquals("ai_model_diagnostic_runs",
                AiModelDiagnosticRun.class.getAnnotation(TableName.class).value());
        assertEquals(String.class, AiModelDiagnosticRun.class.getDeclaredField("probeVersion").getType());
        assertEquals(String.class, AiModelDiagnosticRun.class.getDeclaredField("templateVersion").getType());
    }

    @Test
    void abandonedUpdateCannotOverwriteACompletedOrUnexpiredRun() throws Exception {
        Method method = AiModelDiagnosticRunMapper.class.getMethod(
                "markAbandonedIfExpired", Long.class);
        String sql = String.join("\n", method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("status = 'RUNNING'"));
        assertTrue(sql.contains("deadline_at <= NOW(3)"));
        assertTrue(sql.contains("completed_at = NOW(3)"));
        assertTrue(sql.contains("version = version + 1"));

        Method scan = AiModelDiagnosticRunMapper.class.getMethod(
                "selectExpiredRunningIds", int.class);
        String scanSql = String.join("\n", scan.getAnnotation(Select.class).value());
        assertTrue(scanSql.contains("status = 'RUNNING'"));
        assertTrue(scanSql.contains("deadline_at <= NOW(3)"));
        assertTrue(scanSql.contains("LIMIT #{limit}"));
    }

    @Test
    void terminalUpdateAndIdempotencyLockFreezeTheRaceContract() throws Exception {
        Method finish = AiModelDiagnosticRunMapper.class.getMethod(
                "finishRunning", AiModelDiagnosticRun.class);
        String finishSql = String.join("\n", finish.getAnnotation(Update.class).value());
        assertTrue(finishSql.contains("status = 'RUNNING'"));
        assertTrue(finishSql.contains("deadline_at > NOW(3)"));
        assertTrue(finishSql.contains("completed_at = NOW(3)"));
        assertTrue(finishSql.contains("GREATEST(0"));

        Method reject = AiModelDiagnosticRunMapper.class.getMethod(
                "rejectRunningBeforeExecution",
                Long.class, String.class, String.class, String.class);
        String rejectSql = String.join("\n", reject.getAnnotation(Update.class).value());
        assertTrue(rejectSql.contains("status = 'REJECTED'"));
        assertTrue(rejectSql.contains("status = 'RUNNING'"));
        assertTrue(rejectSql.contains("deadline_at > NOW(3)"));
        assertTrue(rejectSql.contains("version = version + 1"));
        assertTrue(rejectSql.contains("GREATEST(0"));

        Method idempotencyLock = AiModelDiagnosticRunMapper.class.getMethod(
                "selectByIdempotencyKeyForUpdate", Long.class, String.class);
        String lockSql = String.join("\n", idempotencyLock.getAnnotation(Select.class).value());
        assertTrue(lockSql.contains("operator_id = #{operatorId}"));
        assertTrue(lockSql.contains("client_request_id = #{clientRequestId}"));
        assertTrue(lockSql.contains("FOR UPDATE"));
    }

    @Test
    void freeChatContextAndMapperScanAreBoundedInsideTheDiagnosticModule() throws Exception {
        Method context = AiModelDiagnosticRunMapper.class.getMethod(
                "selectRecentSuccessfulFreeChatContext", Long.class, Integer.class);
        String contextSql = String.join("\n", context.getAnnotation(Select.class).value());
        assertTrue(contextSql.contains("test_mode = 'FREE_CHAT'"));
        assertTrue(contextSql.contains("turn_no < #{beforeTurnNo}"));
        assertTrue(contextSql.contains("ORDER BY turn_no DESC"));
        assertTrue(contextSql.contains("LIMIT 9"));

        Method freeze = AiModelDiagnosticRunMapper.class.getMethod(
                "freezeRequestMessagesIfRunning", Long.class, String.class);
        String freezeSql = String.join("\n", freeze.getAnnotation(Update.class).value());
        assertTrue(freezeSql.contains("request_messages_json = #{requestMessagesJson}"));
        assertTrue(freezeSql.contains("status = 'RUNNING'"));
        assertTrue(freezeSql.contains("deadline_at > NOW(3)"));

        MapperScan mapperScan = ModelDiagnosticMapperConfiguration.class
                .getAnnotation(MapperScan.class);
        assertEquals("com.huanjing.geo.module.system.modeldiagnostic.mapper",
                mapperScan.value()[0]);
    }

    @Test
    void historyListIsOwnerScopedAndCannotHydrateSensitivePayloadColumns() throws Exception {
        Method count = AiModelDiagnosticRunMapper.class.getMethod(
                "countOwnedHistory", Long.class, ModelDiagnosticHistoryQuery.class);
        String countSql = String.join("\n", count.getAnnotation(Select.class).value());
        assertTrue(countSql.contains("operator_id = #{operatorId}"));

        Method page = AiModelDiagnosticRunMapper.class.getMethod(
                "selectOwnedHistory", Long.class, ModelDiagnosticHistoryQuery.class,
                long.class, int.class);
        String pageSql = String.join("\n", page.getAnnotation(Select.class).value());
        assertTrue(pageSql.contains("operator_id = #{operatorId}"));
        assertTrue(pageSql.contains("ORDER BY created_at DESC, id DESC"));
        assertTrue(pageSql.contains("LIMIT #{limit} OFFSET #{offset}"));
        assertFalse(pageSql.contains("SELECT *"));
        assertFalse(pageSql.contains("answer,"));
        assertFalse(pageSql.contains("request_messages_json"));
        assertFalse(pageSql.contains("sanitized_request"));
        assertFalse(pageSql.contains("sanitized_response"));
        assertFalse(pageSql.contains("config_snapshot_json"));

        Method sessionRuns = AiModelDiagnosticRunMapper.class.getMethod(
                "selectOwnedSessionRuns", Long.class, String.class);
        String sessionSql = String.join(
                "\n", sessionRuns.getAnnotation(Select.class).value());
        assertTrue(sessionSql.contains("operator_id = #{operatorId}"));
        assertTrue(sessionSql.contains("session_id = #{sessionId}"));
        assertFalse(sessionSql.contains("SELECT *"));
        assertFalse(sessionSql.contains("request_fingerprint"));
        assertFalse(sessionSql.contains("config_snapshot_json"));
        assertFalse(sessionSql.contains("config_snapshot_hash"));
        assertFalse(sessionSql.contains("endpoint_url"));

        Method detail = AiModelDiagnosticRunMapper.class.getMethod(
                "selectOwnedRun", Long.class, Long.class);
        String detailSql = String.join("\n", detail.getAnnotation(Select.class).value());
        assertFalse(detailSql.contains("SELECT *"));
        assertFalse(detailSql.contains("request_fingerprint"));
        assertFalse(detailSql.contains("config_snapshot_json"));
        assertFalse(detailSql.contains("config_snapshot_hash"));
        assertFalse(detailSql.contains("endpoint_url"));
        assertTrue(detailSql.contains("sanitized_request"));
        assertTrue(detailSql.contains("sanitized_response"));
    }

    @Test
    void retentionDeletesAreStrictlyBeforeCutoffBoundedAndSessionSafe() throws Exception {
        Method runs = AiModelDiagnosticRunMapper.class.getMethod(
                "deleteExpiredBatch", java.time.LocalDateTime.class, int.class);
        String runSql = String.join("\n", runs.getAnnotation(Delete.class).value());
        assertTrue(runSql.contains("created_at < #{cutoff}"));
        assertTrue(runSql.contains("ORDER BY created_at ASC, id ASC"));
        assertTrue(runSql.contains("LIMIT #{limit}"));

        Method sessions = AiModelDiagnosticSessionMapper.class.getMethod(
                "deleteEmptyExpiredBatch", java.time.LocalDateTime.class, int.class);
        String sessionSql = String.join(
                "\n", sessions.getAnnotation(Delete.class).value());
        assertTrue(sessionSql.contains("COALESCE(session_row.last_run_at, session_row.created_at) < #{cutoff}"));
        assertTrue(sessionSql.contains("NOT EXISTS"));
        assertTrue(sessionSql.contains("run_row.session_record_id = session_row.id"));
        assertTrue(sessionSql.contains("LIMIT #{limit}"));
    }

    private String completeMigrationContract() throws Exception {
        return Files.readString(V315_MIGRATION) + "\n" + Files.readString(V316_MIGRATION);
    }
}
