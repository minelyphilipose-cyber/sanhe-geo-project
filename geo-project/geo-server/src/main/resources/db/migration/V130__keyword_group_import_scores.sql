SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'question_code'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN question_code VARCHAR(32) NULL COMMENT ''imported question id/code'' AFTER keyword_text',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'score_relevance'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN score_relevance DECIMAL(5,2) NULL COMMENT ''commercial value score'' AFTER monitor_frequency',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'score_intent'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN score_intent DECIMAL(5,2) NULL COMMENT ''conversion distance score'' AFTER score_relevance',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'score_competition'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN score_competition DECIMAL(5,2) NULL COMMENT ''brand binding score'' AFTER score_intent',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'score_conversion'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN score_conversion DECIMAL(5,2) NULL COMMENT ''region industry score'' AFTER score_competition',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'score_coverage'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN score_coverage DECIMAL(5,2) NULL COMMENT ''phase one feasibility score'' AFTER score_conversion',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND index_name = 'idx_keyword_group_result_code'
);
SET @ddl_sql := IF(@idx_exists = 0,
    'ALTER TABLE keyword_group_result ADD KEY idx_keyword_group_result_code (group_id, question_code)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;
