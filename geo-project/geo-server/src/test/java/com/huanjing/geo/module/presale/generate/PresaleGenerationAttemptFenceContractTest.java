package com.huanjing.geo.module.presale.generate;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PresaleGenerationAttemptFenceContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V346__presale_generation_attempt_fence.sql");
    private static final Path VERSION_MAPPER = Path.of(
            "src/main/resources/mapper/presale/persist/PresaleReportVersionMapper.xml");
    private static final Path CALL_MAPPER = Path.of(
            "src/main/resources/mapper/presale/persist/PresaleAiCallMapper.xml");
    private static final Path RESULT_MAPPER = Path.of(
            "src/main/resources/mapper/presale/persist/PresaleAiPromptResultMapper.xml");

    @Test
    void migrationAndMappersFenceEveryAttemptOwnedWrite() throws Exception {
        String migration = Files.readString(MIGRATION);
        String versionSql = Files.readString(VERSION_MAPPER);
        String callSql = Files.readString(CALL_MAPPER);
        String resultSql = Files.readString(RESULT_MAPPER);

        assertTrue(migration.contains("generation_attempt BIGINT NOT NULL DEFAULT 0"));
        assertTrue(versionSql.contains("generation_attempt = generation_attempt + 1"));
        assertTrue(versionSql.contains("generation_status = 'QUEUED'"));

        assertCurrentRunGuard(callSql);
        assertCurrentRunGuard(resultSql);
        assertTrue(resultSql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(resultSql.contains("LAST_INSERT_ID(presale_ai_prompt_result.id)"));
        assertTrue(resultSql.contains("COALESCE(#{row.effectiveSample}, TRUE)"));
        assertTrue(resultSql.contains("query_call_id = VALUES(query_call_id)"));
        assertTrue(resultSql.contains("analyze_call_id = VALUES(analyze_call_id)"));
    }

    private void assertCurrentRunGuard(String sql) {
        assertTrue(sql.contains("v.generation_status = 'RUNNING'"));
        assertTrue(sql.contains("v.generation_attempt = #{generationAttempt}"));
    }
}
