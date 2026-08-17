package com.huanjing.geo.module.extension.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionRuntimeStatusPlatformMigrationContractTest {

    @Test
    void migrationScopesRuntimeIdentityByNormalizedPlatform() throws IOException {
        String resource = "/db/migration/V358__scope_extension_runtime_status_by_platform.sql";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, "missing migration " + resource);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertTrue(sql.contains("drop index uk_extension_runtime_provider_install"));
            assertTrue(sql.contains("runtime_platform_key"));
            assertTrue(sql.contains("detected_platform"));
            assertTrue(sql.contains("uk_extension_runtime_provider_install_platform"));
            assertFalse(sql.contains("unique key uk_extension_runtime_provider_install ("));
        }
    }
}
