package com.huanjing.geo.module.presale.generate;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PresaleHybridQueryMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V339__hybrid_presale_query_mimo_360.sql");

    @Test
    void migrationAddsWebDenominatorAndDisabledCompanionsWithoutDuplicatingSecrets() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("planned_web_query_count INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("'MIMO_CHAT_WEB'"));
        assertTrue(sql.contains("'QIHOO_360_AI_SEARCH_WEB'"));
        assertTrue(sql.contains("CONCAT('db://ai-platform-config/', base.id)"));
        assertTrue(sql.contains("2, 0, 0, 0, 0, 0, 0, 0,"));
        assertTrue(sql.contains("platform_code IN ('kimi', 'hailuo')"));
    }
}
