package com.huanjing.geo.module.presale.generate;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PresaleMimoBaseTimeoutMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V348__set_mimo_base_timeout_90s.sql");

    @Test
    void migrationOnlyUpdatesNativeMimoConfiguration() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("timeout_ms = 90000"));
        assertTrue(sql.contains("platform_code = 'mimo'"));
        assertTrue(sql.contains("usage_scene = 'STANDARD_CHAT'"));
        assertTrue(sql.contains("config_version = config_version + 1"));
    }
}
