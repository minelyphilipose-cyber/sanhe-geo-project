SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND COLUMN_NAME = 'effective_hit'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE poll_results ADD COLUMN effective_hit TINYINT(1) NULL COMMENT ''裁判模型有效命中'' AFTER is_hit',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND COLUMN_NAME = 'judge_status'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE poll_results ADD COLUMN judge_status VARCHAR(20) NOT NULL DEFAULT ''skipped'' COMMENT ''裁判状态: skipped/success/failed'' AFTER contact_mention_count',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND COLUMN_NAME = 'hit_level'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE poll_results ADD COLUMN hit_level VARCHAR(20) NULL COMMENT ''有效命中等级: strong/normal/weak/invalid'' AFTER judge_status',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND COLUMN_NAME = 'hit_sentiment'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE poll_results ADD COLUMN hit_sentiment VARCHAR(20) NULL COMMENT ''命中情感: positive/neutral/negative/unknown'' AFTER hit_level',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND COLUMN_NAME = 'mention_type'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE poll_results ADD COLUMN mention_type VARCHAR(32) NULL COMMENT ''品牌提及类型'' AFTER hit_sentiment',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND COLUMN_NAME = 'judge_evidence'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE poll_results ADD COLUMN judge_evidence VARCHAR(500) NULL COMMENT ''裁判判定依据'' AFTER mention_type',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND COLUMN_NAME = 'judge_risk_reason'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE poll_results ADD COLUMN judge_risk_reason VARCHAR(500) NULL COMMENT ''无效命中原因'' AFTER judge_evidence',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND COLUMN_NAME = 'judge_model'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE poll_results ADD COLUMN judge_model VARCHAR(128) NULL COMMENT ''裁判模型'' AFTER judge_risk_reason',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND COLUMN_NAME = 'judge_at'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE poll_results ADD COLUMN judge_at DATETIME NULL COMMENT ''裁判时间'' AFTER judge_model',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND COLUMN_NAME = 'judge_error'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE poll_results ADD COLUMN judge_error VARCHAR(500) NULL COMMENT ''裁判失败原因'' AFTER judge_at',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND INDEX_NAME = 'idx_poll_result_effective_hit'
);
SET @sql := IF(@idx = 0,
    'CREATE INDEX idx_poll_result_effective_hit ON poll_results (project_id, effective_hit, batch_date DESC)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
