ALTER TABLE self_media_publish_schedule
  ADD KEY idx_self_media_schedule_execution_dedupe (
    article_id,
    self_media_account_id,
    platform,
    id,
    status
  );
