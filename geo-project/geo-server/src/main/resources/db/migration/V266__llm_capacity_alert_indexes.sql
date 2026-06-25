-- Support lightweight LLM capacity alert scans over Hunyuan/Yuanbao daily poll slices.

SET @idx := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'poll_batch_shards'
               AND INDEX_NAME = 'idx_poll_shard_tier_date_platform');
SET @sql := IF(@idx = 0,
    'ALTER TABLE poll_batch_shards ADD INDEX idx_poll_shard_tier_date_platform (question_tier, batch_date, platform_code)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
