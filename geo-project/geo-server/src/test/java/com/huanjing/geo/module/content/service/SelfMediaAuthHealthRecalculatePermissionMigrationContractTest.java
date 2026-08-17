package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaAuthHealthRecalculatePermissionMigrationContractTest {

    @Test
    void migrationAddsIndependentRecalculatePermissionAndPreservesExistingScope() throws IOException {
        String resource = "/db/migration/V361__add_self_media_auth_health_recalculate_permission.sql";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            assertTrue(sql.contains("'self-media.auth-health.recalculate'"));
            assertTrue(sql.contains("insert into sys_role_permission"));
            assertTrue(sql.contains("'self-media.auth-health.policy-manage', 'company.update'"));
        }
    }
}
