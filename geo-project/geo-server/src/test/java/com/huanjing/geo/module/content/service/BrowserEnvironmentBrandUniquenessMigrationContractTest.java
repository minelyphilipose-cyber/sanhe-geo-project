package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserEnvironmentBrandUniquenessMigrationContractTest {

    @Test
    void migrationPreservesLatestEnvironmentAndAddsBrandActiveUniqueness() throws IOException {
        String resource = "/db/migration/V360__enforce_single_active_browser_environment_per_brand.sql";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            assertTrue(sql.contains("partition by environment.brand_id"));
            assertTrue(sql.contains("order by environment.updated_at desc, environment.id desc"));
            assertTrue(sql.contains("update browser_environment_account"));
            assertTrue(sql.contains("set account_binding.login_status = 'login_required'"));
            assertTrue(sql.contains("last_login_verification_method = 'browser_binding_invalidated'"));
            assertTrue(sql.contains("set account_binding.browser_environment_id = primary_environment.browser_environment_id"));
            assertTrue(sql.contains("set agent_binding.status = 'disabled'"));
            assertTrue(sql.contains("set environment.status = 'deleted'"));
            assertTrue(sql.contains("add unique key uk_browser_env_brand_active (brand_id, active_unique_key)"));
            assertFalse(sql.contains("delete from browser_environment"));
        }
    }
}
