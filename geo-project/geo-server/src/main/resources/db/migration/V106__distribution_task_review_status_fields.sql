-- Add review tracking fields used by self-media distribution adapters.
-- The entity/service fields were introduced after the self_media_account migration;
-- keep this migration narrow so existing distribution data remains untouched.

ALTER TABLE distribution_tasks
  ADD COLUMN external_status VARCHAR(64) NULL COMMENT '平台原始/外部状态' AFTER platform_article_id,
  ADD COLUMN review_status VARCHAR(32) NULL COMMENT '平台审核状态归一化值' AFTER external_status,
  ADD COLUMN review_feedback VARCHAR(1000) NULL COMMENT '平台审核反馈/拒审原因' AFTER review_status;

CREATE INDEX idx_distribution_review_status
  ON distribution_tasks (review_status);
