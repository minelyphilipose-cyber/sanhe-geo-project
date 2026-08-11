package com.huanjing.geo.module.retention.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollRetentionCollationMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V335__align_poll_retention_slice_collation.sql");

    @Test
    void alignsRetentionSliceQuestionTierWithPollResultsCollation() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("FROM information_schema.columns"));
        assertEquals(2, occurrences(sql, "table_schema = DATABASE()"));
        assertEquals(2, occurrences(sql, "character_maximum_length = 16"));
        assertEquals(2, occurrences(sql, "character_set_name = 'utf8mb4'"));
        assertEquals(2, occurrences(sql, "collation_name = 'utf8mb4_0900_ai_ci'"));
        assertEquals(2, occurrences(sql, "is_nullable = 'NO'"));
        assertEquals(2, occurrences(sql, "'SELECT 1'"));
        assertTrue(sql.contains("'ALTER TABLE data_retention_purged_slice"));
        assertTrue(sql.contains("'ALTER TABLE data_retention_recompute_slice_lock"));
        assertEquals(2, occurrences(sql, "MODIFY COLUMN question_tier VARCHAR(16)"));
        assertEquals(2, occurrences(sql, "CHARACTER SET utf8mb4"));
        assertEquals(2, occurrences(sql, "COLLATE utf8mb4_0900_ai_ci"));
        assertEquals(2, occurrences(sql, "NOT NULL"));
        assertEquals(2, occurrences(sql, "\nPREPARE v335_"));
        assertEquals(2, occurrences(sql, "\nEXECUTE v335_"));
        assertEquals(2, occurrences(sql, "\nDEALLOCATE PREPARE v335_"));
    }

    private int occurrences(String value, String token) {
        return value.split(Pattern.quote(token), -1).length - 1;
    }
}
