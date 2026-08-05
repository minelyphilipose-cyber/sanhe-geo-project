package com.huanjing.geo.module.presale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PresaleRepresentedBrandsMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V344__presale_report_represented_brands.sql");

    @Test
    void migrationAddsOptionalJsonFieldToPresaleReport() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("ALTER TABLE presale_report"));
        assertTrue(sql.contains("ADD COLUMN represented_brands JSON NULL"));
        assertTrue(sql.contains("AFTER industry_role"));
    }
}
