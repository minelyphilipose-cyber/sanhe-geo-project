ALTER TABLE project
    ADD COLUMN activated_at DATETIME NULL COMMENT 'first activation datetime' AFTER signed_at;

