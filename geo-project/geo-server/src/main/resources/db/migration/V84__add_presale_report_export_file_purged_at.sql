ALTER TABLE presale_report_export
    ADD COLUMN file_purged_at DATETIME NULL
    COMMENT '导出文件及关联产物清理时间';

CREATE INDEX idx_presale_export_expire_purge
    ON presale_report_export (expire_at, file_purged_at, status);
