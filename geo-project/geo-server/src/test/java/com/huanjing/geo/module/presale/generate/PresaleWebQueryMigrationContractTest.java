package com.huanjing.geo.module.presale.generate;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PresaleWebQueryMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V338__presale_web_query_v1.sql");

    @Test
    void legacyVersionsStayOffAndWebEvidenceIsPersisted() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("query_web_mode VARCHAR(16) NOT NULL DEFAULT 'OFF'"));
        assertTrue(sql.contains("query_contract_version VARCHAR(32) NULL"));
        assertTrue(sql.contains("search_evidence_json MEDIUMTEXT NULL"));
        assertTrue(sql.contains("effective_sample TINYINT(1) NOT NULL DEFAULT 1"));
        assertTrue(sql.contains("planned_query_count INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("degraded_excluded_sample_count INT NOT NULL DEFAULT 0"));
    }
}
