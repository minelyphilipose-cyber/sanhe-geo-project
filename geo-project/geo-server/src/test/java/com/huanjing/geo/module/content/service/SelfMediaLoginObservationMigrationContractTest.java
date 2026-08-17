package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaLoginObservationMigrationContractTest {

    @Test
    void migrationOnlyAddsObservationAndOperationsStructuresWithoutRewritingLoginFacts() throws IOException {
        String resource = "/db/migration/V363__self_media_login_observations_and_operations.sql";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            assertTrue(sql.contains("create table if not exists self_media_login_observation"));
            assertTrue(sql.contains("create table if not exists self_media_auth_work_order"));
            assertTrue(sql.contains("create table if not exists self_media_auth_work_order_event"));
            assertTrue(sql.contains("unique key uk_auth_work_order_brand (brand_id)"));
            assertTrue(sql.contains("add column claimed_by"));
            assertFalse(sql.contains("update self_media_account"));
            assertFalse(sql.contains("update browser_environment_account"));
        }
    }
}
