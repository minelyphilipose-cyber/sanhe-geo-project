-- Allow WeChat all-network release checks to persist temporary authorizers
-- before an account is attached to a brand through the normal admin flow.
ALTER TABLE self_media_account
  MODIFY COLUMN brand_id BIGINT NULL COMMENT '所属品牌，微信全网发布检测账号可为空';
