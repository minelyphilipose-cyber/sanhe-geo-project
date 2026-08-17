package com.huanjing.geo.module.system.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemAlertSnoozeMigrationContractTest {

    @Test
    void migrationAddsSnoozeDeadlineAndOpenAlertLookupIndex() throws IOException {
        String resource = "/db/migration/V362__system_alert_snooze.sql";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            assertTrue(sql.contains("add column snoozed_until datetime null"));
            assertTrue(sql.contains("idx_system_alert_snoozed (is_resolved, snoozed_until)"));
        }
    }
}
