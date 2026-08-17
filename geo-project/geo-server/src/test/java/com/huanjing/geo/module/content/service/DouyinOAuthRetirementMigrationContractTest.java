package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DouyinOAuthRetirementMigrationContractTest {

    @Test
    void migrationDisablesLegacyOAuthAndAlignsReferenceWindows() throws IOException {
        String resource = "/db/migration/V355__retire_douyin_oauth_and_align_login_validity_reference.sql";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            assertTrue(sql.contains("lower(account.platform) in ('douyin', 'douyin_image_text')"));
            assertTrue(sql.contains("set account.auth_mode = 'cookie'"));
            assertTrue(sql.contains("binding.deleted_at is null"));
            assertTrue(sql.contains("environment.status = 'active'"));
            assertTrue(sql.contains("upper(coalesce(account.auth_mode, 'oauth')) = 'oauth'"));
            assertTrue(sql.contains("account.status = 'active'"));
            assertTrue(sql.contains("account.status = 'disabled'"));
            assertTrue(sql.contains("and not exists"));
            assertTrue(sql.contains("set reverify_interval_days = case"));
            assertTrue(sql.contains("warning_days = case"));
            assertTrue(sql.contains("when platform_code in ('douyin', 'xiaohongshu') then 30"));
            assertTrue(sql.contains("when platform_code in ('douyin', 'xiaohongshu') then 7"));
            assertTrue(sql.contains("else 14"));
            assertTrue(sql.contains("else 90"));
            assertTrue(sql.contains("credential_expiry_mode = 'declared_then_reference'"));
            assertTrue(sql.contains("credential.expiry_source = 'platform_policy'"));
        }
    }
}
