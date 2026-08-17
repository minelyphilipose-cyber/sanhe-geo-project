package com.huanjing.geo.module.retention.website;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebsitePayloadGuardMigrationContractTest {

    @Test
    void migrationRepairsMarkerOnlyAndInstallsNamedCheck() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/"
                + "V366__guard_distribution_payload_purge_marker.sql"));

        assertTrue(sql.contains("SET payload_purged_at = NULL"));
        assertTrue(sql.contains("request_payload IS NOT NULL"));
        assertTrue(sql.contains("fill_payload IS NOT NULL"));
        assertTrue(sql.contains("response_payload IS NOT NULL"));
        assertTrue(sql.contains("chk_distribution_tasks_payload_purge_consistency"));
        assertTrue(sql.contains("payload_purged_at IS NULL OR"));
        assertFalse(sql.contains("SET request_payload ="));
        assertFalse(sql.contains("SET fill_payload ="));
        assertFalse(sql.contains("SET response_payload ="));
    }

    @Test
    void payloadWritersResetThePurgeMarkerAndDefaultsStayDisabled() throws Exception {
        String distribution = Files.readString(Path.of("src/main/java/com/huanjing/geo/module/content/service/"
                + "ContentDistributionService.java"));
        String official = Files.readString(Path.of("src/main/java/com/huanjing/geo/module/content/schedule/"
                + "OfficialApiSelfMediaPublishScheduleAdapter.java"));
        String reviewPoll = Files.readString(Path.of("src/main/java/com/huanjing/geo/module/content/service/"
                + "DistributionReviewStatusPollService.java"));
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(distribution.contains("setPayloadPurgedAt(null)"));
        assertTrue(official.contains("setPayloadPurgedAt(null)"));
        assertTrue(reviewPoll.contains("set(DistributionTask::getPayloadPurgedAt, null)"));
        assertTrue(yaml.contains("GEO_RETENTION_WEBSITE_PUBLISHED_CLEANUP_EXECUTE_ENABLED:false"));
        assertTrue(yaml.contains("GEO_RETENTION_DYNAMIC_SWITCH_ENABLE_WRITE_ENABLED:false"));
    }
}
