-- Use the concrete Douyin article editor route for AdsPower automation.

UPDATE self_media_schedule_capability
SET evidence_json = JSON_SET(
      COALESCE(evidence_json, JSON_OBJECT()),
      '$.publishUrl',
      'https://creator.douyin.com/creator-micro/content/post/article?media_type=article&type=new&enter_from=publish_page'
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE platform = 'douyin';
