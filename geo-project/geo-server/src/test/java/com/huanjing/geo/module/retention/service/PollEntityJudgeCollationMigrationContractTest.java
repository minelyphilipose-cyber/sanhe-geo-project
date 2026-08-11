package com.huanjing.geo.module.retention.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollEntityJudgeCollationMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V336__align_poll_entity_judge_collation.sql");

    @Test
    void alignsOnlyTheDownstreamPlatformDimensionIdempotently() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertEquals(1, occurrences(sql, "FROM information_schema.columns"));
        assertEquals(1, occurrences(sql, "table_schema = DATABASE()"));
        assertTrue(sql.contains("table_name = 'poll_entity_judge_daily_summary'"));
        assertTrue(sql.contains("column_name = 'platform_code'"));
        assertEquals(1, occurrences(sql, "character_maximum_length = 64"));
        assertEquals(1, occurrences(sql, "character_set_name = 'utf8mb4'"));
        assertEquals(1, occurrences(sql, "collation_name = 'utf8mb4_0900_ai_ci'"));
        assertEquals(1, occurrences(sql, "is_nullable = 'YES'"));
        assertEquals(1, occurrences(sql, "'SELECT 1'"));
        assertTrue(sql.contains("'ALTER TABLE poll_entity_judge_daily_summary"));
        assertEquals(1, occurrences(sql,
                "MODIFY COLUMN platform_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL"));
        assertFalse(sql.contains("ALTER TABLE poll_result_entity_judge"));
        assertFalse(sql.contains("MODIFY COLUMN question_tier"));
        assertEquals(1, occurrences(sql, "\nPREPARE v336_"));
        assertEquals(1, occurrences(sql, "\nEXECUTE v336_"));
        assertEquals(1, occurrences(sql, "\nDEALLOCATE PREPARE v336_"));
    }

    private int occurrences(String value, String token) {
        return value.split(Pattern.quote(token), -1).length - 1;
    }
}
