package com.huanjing.geo.module.presale.generate;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MimoWebTimeoutMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V343__set_mimo_web_timeout_120s.sql");

    @Test
    void migrationOnlyChangesMimoWebTimeoutToOneHundredTwentySeconds() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("timeout_ms = 120000"));
        assertTrue(sql.contains("platform_code = 'mimo_web'"));
        assertTrue(sql.contains("usage_scene = 'QUESTION_POLL_WEB'"));
        assertTrue(sql.contains("integration_type = 'MIMO_CHAT_WEB'"));
    }
}
