package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaAccountIdentityPolicyMigrationContractTest {

    @Test
    void migrationClearsOnlyObsoleteSuccessfulIdMismatchWarnings() throws IOException {
        String resource = "/db/migration/V357__clear_obsolete_platform_account_id_warnings.sql";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            assertTrue(sql.contains("set last_login_verification_warning = null"));
            assertTrue(sql.contains("last_login_verification_result = 'success'"));
            assertTrue(sql.contains("平台账号 id 与系统记录不同"));
            assertTrue(sql.contains("result_code = 'success'"));
            assertTrue(sql.contains("result_code = 'success_with_id_warning'"));
            assertFalse(sql.contains("last_login_verified_at = null"));
        }
    }
}
