ALTER TABLE self_media_publish_schedule
  ADD COLUMN publish_payload_json JSON NULL
    COMMENT 'Immutable platform publish payload snapshot, including Douyin image-text data'
    AFTER diagnostics_json;
