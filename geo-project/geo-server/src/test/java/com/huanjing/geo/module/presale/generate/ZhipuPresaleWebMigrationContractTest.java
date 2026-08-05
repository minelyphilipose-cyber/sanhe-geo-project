package com.huanjing.geo.module.presale.generate;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ZhipuPresaleWebMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V340__restore_zhipu_web_for_presale_query.sql");
    private static final Path SIMPLIFICATION = Path.of(
            "src/main/resources/db/migration/V341__simplify_zhipu_presale_chat_search.sql");

    @Test
    void migrationRestoresZhipuOnlyForPresaleWebRoutingAndReusesBaseCredential() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("companion.integration_type = 'ZHIPU_CHAT_WEB'"));
        assertTrue(sql.contains("CONCAT('db://ai-platform-config/', base.id)"));
        assertTrue(sql.contains("companion.enabled = 1"));
        assertTrue(sql.contains("companion.enabled_for_question_poll = 0"));
        assertTrue(sql.contains("companion.enabled_for_mobile_dashboard = 0"));
        assertTrue(sql.contains("'glm-4.7-flashx'"));
        assertTrue(sql.contains("companion.degraded_reason = NULL"));
        String simplification = Files.readString(SIMPLIFICATION);
        assertTrue(simplification.contains("JSON_REMOVE(provider_config_json, '$.searchEndpointUrl')"));
        assertTrue(simplification.contains("native glm-4.7-flashx Chat Search"));
    }
}
