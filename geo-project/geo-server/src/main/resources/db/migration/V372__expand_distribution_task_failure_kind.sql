ALTER TABLE distribution_tasks
  MODIFY COLUMN failure_kind VARCHAR(64) NULL
  COMMENT 'structured failure category';
