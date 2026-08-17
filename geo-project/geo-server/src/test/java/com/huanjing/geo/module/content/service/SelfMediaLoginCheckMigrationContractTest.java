package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaLoginCheckMigrationContractTest {

    @Test
    void migrationSeparatesLatestCheckFromLastSuccessfulVerification() throws IOException {
        String resource = "/db/migration/V356__track_latest_self_media_login_check.sql";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            assertTrue(sql.contains("add column last_login_verification_checked_at datetime null"));
            assertTrue(sql.contains("set last_login_verification_checked_at = last_login_verified_at"));
            assertTrue(sql.contains("where last_login_verified_at is not null"));
        }
    }
}
