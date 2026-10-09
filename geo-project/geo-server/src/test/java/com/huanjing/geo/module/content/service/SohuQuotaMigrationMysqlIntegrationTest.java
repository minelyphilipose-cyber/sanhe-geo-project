package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.retention.support.RetentionExternalTestServices;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class SohuQuotaMigrationMysqlIntegrationTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void backfillsOnlySohuPreservesSignedRightsAndUsageAndCanBeRepeated() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(RetentionExternalTestServices.mysqlConfigured(),
                "External MySQL is required to verify the JSON migration");
        try (var mysql = RetentionExternalTestServices.createMysql("geo_retention_sohu_it_")) {
            JdbcTemplate jdbc = new JdbcTemplate(mysql.dataSource());
            jdbc.execute("""
                    CREATE TABLE package_channel_quota_config (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT, package_plan_id BIGINT NOT NULL,
                      channel_code VARCHAR(64), period_type VARCHAR(16), quota_limit INT, enabled BOOLEAN,
                      UNIQUE KEY uk_quota (package_plan_id, channel_code, period_type))
                    """);
            jdbc.execute("""
                    CREATE TABLE company_package_binding (
                      id BIGINT PRIMARY KEY, company_id BIGINT, package_plan_id BIGINT,
                      status VARCHAR(20), active_flag INT, channel_quota_snapshot JSON,
                      package_snapshot_json JSON, partner_visible_snapshot_json JSON,
                      internal_delivery_snapshot_json JSON)
                    """);
            jdbc.execute("""
                    CREATE TABLE company_channel_quota_usage (
                      id BIGINT PRIMARY KEY, company_id BIGINT, channel_code VARCHAR(64),
                      period_type VARCHAR(16), period_key VARCHAR(32), quota_limit INT, used_count INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE self_media_schedule_capability (
                      platform VARCHAR(32) PRIMARY KEY, verification_status VARCHAR(32),
                      supports_schedule BOOLEAN, v1_strategy VARCHAR(32), notes TEXT)
                    """);
            jdbc.execute("CREATE TABLE company_channel_quota_ledger (id BIGINT PRIMARY KEY, status VARCHAR(32))");
            jdbc.update("""
                    INSERT INTO package_channel_quota_config
                      (package_plan_id, channel_code, period_type, quota_limit, enabled) VALUES
                      (1, 'self_media:toutiao', 'month', 12, TRUE),
                      (1, 'self_media:sohu', 'week', 0, FALSE),
                      (1, 'forum', 'week', 2, TRUE),
                      (2, 'self_media:toutiao', 'week', 3, FALSE)
                    """);
            String signed = """
                    [{"channelCode":"self_media:toutiao","periodType":"month","quotaLimit":5,"enabled":true},
                     {"channelCode":"forum","periodType":"week","quotaLimit":7,"enabled":true}]
                    """;
            String withSohu = """
                    [{"channelCode":"self_media:toutiao","periodType":"month","quotaLimit":8,"enabled":true},
                     {"channelCode":"self_media:sohu","periodType":"week","quotaLimit":0,"enabled":false}]
                    """;
            String nested = "{\"packageName\":\"signed-name\",\"channelQuotaSnapshot\":" + signed + "}";
            String partner = """
                    {"packageName":"signed-name","channelQuotaSnapshot":[
                      {"channelCode":"self_media:toutiao","periodType":"month","quotaLimit":5,"enabled":true}]}
                    """;
            jdbc.update("INSERT INTO company_package_binding VALUES (1, 10, 1, 'active', 1, ?, ?, ?, ?)",
                    signed, nested, partner, nested);
            jdbc.update("INSERT INTO company_package_binding VALUES (2, 20, 1, 'active', 1, ?, NULL, NULL, NULL)", withSohu);
            jdbc.update("INSERT INTO company_package_binding VALUES (3, 30, 1, 'inactive', NULL, ?, NULL, NULL, NULL)", signed);
            jdbc.update("INSERT INTO company_package_binding VALUES (4, 40, 1, 'active', 1, '[]', NULL, NULL, NULL)");
            jdbc.update("INSERT INTO company_package_binding VALUES (5, 50, 2, 'active', 1, '[]', NULL, NULL, NULL)");
            String currentMonth = jdbc.queryForObject(
                    "SELECT DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y-%m')", String.class);
            jdbc.update("INSERT INTO company_channel_quota_usage VALUES (1, 10, 'self_media:sohu', 'month', ?, 0, 2)", currentMonth);
            jdbc.update("INSERT INTO company_channel_quota_usage VALUES (2, 10, 'self_media:sohu', 'month', '2000-01', 9, 4)");
            jdbc.update("INSERT INTO company_channel_quota_usage VALUES (3, 10, 'self_media:toutiao', 'month', ?, 5, 3)", currentMonth);
            jdbc.update("INSERT INTO company_channel_quota_ledger VALUES (1, 'reserved')");

            for (int attempt = 0; attempt < 2; attempt++) {
                try (var connection = mysql.dataSource().getConnection()) {
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource(Path.of(
                            "src/main/resources/db/migration/V373__open_sohu_account_configuration_and_quota.sql")));
                }
                assertEquals("month", jdbc.queryForObject("SELECT period_type FROM package_channel_quota_config WHERE package_plan_id=1 AND channel_code='self_media:sohu'", String.class));
                assertEquals(12, jdbc.queryForObject("SELECT quota_limit FROM package_channel_quota_config WHERE package_plan_id=1 AND channel_code='self_media:sohu'", Integer.class));
                JsonNode canonical = snapshot(jdbc, 1, "channel_quota_snapshot");
                assertEquals(3, canonical.size());
                assertEquals(5, sohu(canonical).path("quotaLimit").asInt());
                assertEquals("month", sohu(canonical).path("periodType").asText());
                assertEquals(7, canonical.get(1).path("quotaLimit").asInt());
                for (String column : new String[]{"package_snapshot_json", "partner_visible_snapshot_json", "internal_delivery_snapshot_json"}) {
                    JsonNode object = snapshot(jdbc, 1, column);
                    assertEquals("signed-name", object.path("packageName").asText());
                    assertEquals(5, sohu(object.path("channelQuotaSnapshot")).path("quotaLimit").asInt());
                }
                assertEquals(2, snapshot(jdbc, 1, "partner_visible_snapshot_json").path("channelQuotaSnapshot").size());
                assertEquals(2, snapshot(jdbc, 2, "channel_quota_snapshot").size());
                assertEquals(8, sohu(snapshot(jdbc, 2, "channel_quota_snapshot")).path("quotaLimit").asInt());
                assertNull(snapshot(jdbc, 2, "package_snapshot_json"));
                assertEquals(json.readTree(signed), snapshot(jdbc, 3, "channel_quota_snapshot"));
                assertEquals(12, sohu(snapshot(jdbc, 4, "channel_quota_snapshot")).path("quotaLimit").asInt());
                assertFalse(sohu(snapshot(jdbc, 5, "channel_quota_snapshot")).path("enabled").asBoolean());
                assertEquals(5, jdbc.queryForObject("SELECT quota_limit FROM company_channel_quota_usage WHERE id=1", Integer.class));
                assertEquals(2, jdbc.queryForObject("SELECT used_count FROM company_channel_quota_usage WHERE id=1", Integer.class));
                assertEquals(9, jdbc.queryForObject("SELECT quota_limit FROM company_channel_quota_usage WHERE id=2", Integer.class));
                assertEquals(3, jdbc.queryForObject("SELECT used_count FROM company_channel_quota_usage WHERE id=3", Integer.class));
                assertEquals("reserved", jdbc.queryForObject("SELECT status FROM company_channel_quota_ledger WHERE id=1", String.class));
                assertEquals("unverified", jdbc.queryForObject("SELECT verification_status FROM self_media_schedule_capability WHERE platform='sohu'", String.class));
            }
        }
    }

    private JsonNode snapshot(JdbcTemplate jdbc, int id, String column) throws Exception {
        String raw = jdbc.queryForObject("SELECT " + column + " FROM company_package_binding WHERE id=?", String.class, id);
        return raw == null ? null : json.readTree(raw);
    }

    private JsonNode sohu(JsonNode array) {
        for (JsonNode item : array) {
            if ("self_media:sohu".equals(item.path("channelCode").asText())) return item;
        }
        throw new AssertionError("Sohu quota is missing");
    }
}
