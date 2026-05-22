ALTER TABLE distribution_tasks
  ADD COLUMN platform_publish_id VARCHAR(128) NULL COMMENT '平台提交/发布任务ID，如微信 publish_id' AFTER platform_article_id,
  ADD COLUMN submitted_at DATETIME NULL COMMENT '提交到外部平台成功时间' AFTER review_feedback,
  ADD COLUMN review_checked_at DATETIME NULL COMMENT '最近一次审核状态回查时间' AFTER submitted_at,
  ADD COLUMN next_review_check_at DATETIME NULL COMMENT '下次审核状态回查时间' AFTER review_checked_at,
  ADD COLUMN review_check_count INT NOT NULL DEFAULT 0 COMMENT '审核状态累计回查次数，只增不减' AFTER next_review_check_at,
  ADD COLUMN review_locked_until DATETIME NULL COMMENT '审核回查任务抢占锁过期时间' AFTER review_check_count;

CREATE INDEX idx_distribution_review_poll_due
  ON distribution_tasks (target_kind, dispatch_mode, status, review_status, next_review_check_at);

CREATE INDEX idx_distribution_review_lock
  ON distribution_tasks (review_locked_until);
