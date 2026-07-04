UPDATE project_competitor_config
   SET qa_status = 'passed',
       qa_checked_at = COALESCE(qa_checked_at, CURRENT_TIMESTAMP),
       updated_at = CURRENT_TIMESTAMP
 WHERE qa_status <> 'passed'
    OR qa_status IS NULL;

ALTER TABLE project_competitor_config
    MODIFY qa_status varchar(20) NOT NULL DEFAULT 'passed';
