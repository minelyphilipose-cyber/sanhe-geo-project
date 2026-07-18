-- Use Douyin creator article entry for AdsPower automation.

UPDATE self_media_schedule_capability
SET evidence_json = JSON_SET(
      COALESCE(evidence_json, JSON_OBJECT()),
      '$.publishUrl',
      'https://creator.douyin.com/creator-micro/content/upload?media_type=article&type=new'
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE platform = 'douyin';
