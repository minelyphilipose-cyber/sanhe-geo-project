-- ============================================================
-- V35: partner creator scope + source mark
-- ============================================================

ALTER TABLE company
    ADD COLUMN created_by BIGINT NULL COMMENT 'creator user id' AFTER remark;

ALTER TABLE company
    ADD KEY idx_company_created_by (created_by);

ALTER TABLE company
    ADD CONSTRAINT fk_company_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id);

ALTER TABLE project
    ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'internal' COMMENT 'internal|partner' AFTER owner_type;

ALTER TABLE project
    ADD KEY idx_project_source_type (source_type);
