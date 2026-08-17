package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaAuthWorkOrderResolutionMigrationContractTest {

    @Test
    void migrationOnlyAddsResolutionMetadataWithoutChangingAccountLoginState() throws IOException {
        String resource = "/db/migration/V365__structure_auth_work_order_resolution.sql";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            assertTrue(sql.contains("add column resolution_mode"));
            assertTrue(sql.contains("add column resolution_reason"));
            assertFalse(sql.contains("update self_media_account"));
            assertFalse(sql.contains("update browser_environment_account"));
            assertFalse(sql.contains("last_login_verified_at"));
        }
    }
}
