package com.huanjing.geo.module.retention.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectStorageMigrationServiceTest {

    @ParameterizedTest
    @CsvSource({
            "brand/a.jpg,image/jpeg",
            "brand/a.JPEG,image/jpeg",
            "brand/a.png,image/png",
            "brand/a.gif,image/gif",
            "brand/a.webp,image/webp",
            "ai-platform/logo/a.svg,image/svg+xml",
            "presale/exports/1/report.pdf,application/pdf",
            "archive/article/1.md,text/markdown; charset=utf-8",
            "retention/freeze/1.json,application/json; charset=utf-8",
            "unknown/file.bin,application/octet-stream"
    })
    void shouldResolveContentTypeFromObjectKey(String objectKey, String expectedContentType) {
        assertEquals(expectedContentType, ObjectStorageMigrationService.contentTypeForObjectKey(objectKey));
    }

    @Test
    void shouldRegisterGeneratedReportsButExcludeUnusedBaselineExports() {
        ObjectStorageKeyRegistry registry = new ObjectStorageKeyRegistry();

        assertTrue(registry.columns().stream().anyMatch(column ->
                "reports".equals(column.tableName()) && "reports/".equals(column.managedPrefix())));
        assertFalse(registry.columns().stream().anyMatch(column ->
                "baseline_report_export".equals(column.tableName())));
    }
}
