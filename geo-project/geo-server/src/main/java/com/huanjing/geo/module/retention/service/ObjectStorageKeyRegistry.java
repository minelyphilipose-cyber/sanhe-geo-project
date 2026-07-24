package com.huanjing.geo.module.retention.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ObjectStorageKeyRegistry {

    private static final List<ObjectKeyColumn> COLUMNS = List.of(
            new ObjectKeyColumn("brand_material", "object_key", null,
                    "brand/", false, null, "brand/customer material object"),
            new ObjectKeyColumn("sys_user", "avatar_object_key", null,
                    "user/avatar/", false, null, "user avatar object"),
            new ObjectKeyColumn("presale_report_export", "file_key", null,
                    "presale/exports/", false,
                    "file_purged_at IS NULL AND (expire_at IS NULL OR expire_at > NOW())",
                    "active presale export pdf"),
            new ObjectKeyColumn("presale_report_export", "snapshot_key", null,
                    "presale/exports/", false,
                    "snapshot_storage_type = 'OBJECT' AND file_purged_at IS NULL AND (expire_at IS NULL OR expire_at > NOW())",
                    "active presale export snapshot"),
            new ObjectKeyColumn("ai_platform_config", "platform_logo_object_key", null,
                    "ai-platform/logo/", false, null, "AI platform logo object"),
            new ObjectKeyColumn("reports", "CONCAT('reports/', project_id, '/', report_type, '/latest.pdf')", null,
                    "reports/", false, "pdf_url IS NOT NULL AND pdf_url <> ''", "generated report PDF object"),
            new ObjectKeyColumn("article_draft_version", "content_object_key", "content_checksum",
                    "archive/article/", true, null, "archived article markdown body"),
            new ObjectKeyColumn("report_period_freeze", "snapshot_object_key", "object_checksum",
                    "retention/freeze/report-period/", true, null, "frozen poll detail report snapshot")
    );

    public List<ObjectKeyColumn> columns() {
        return COLUMNS;
    }

    public List<String> managedPrefixes() {
        return COLUMNS.stream()
                .filter(ObjectKeyColumn::orphanScanEnabled)
                .map(ObjectKeyColumn::managedPrefix)
                .distinct()
                .toList();
    }

    public record ObjectKeyColumn(String tableName,
                                  String columnName,
                                  String checksumColumnName,
                                  String managedPrefix,
                                  boolean orphanScanEnabled,
                                  String whereClause,
                                  String note) {
        public String checksumExpression() {
            return checksumColumnName == null || checksumColumnName.isBlank() ? "NULL" : checksumColumnName;
        }
    }
}
