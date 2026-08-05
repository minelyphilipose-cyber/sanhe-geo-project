ALTER TABLE poll_batch_shard_items
    ADD COLUMN result_snapshot_json JSON NULL
        COMMENT 'durable model outcome awaiting poll_results projection' AFTER poll_result_id,
    ADD COLUMN result_snapshot_at DATETIME NULL
        COMMENT 'time the durable model outcome was staged' AFTER result_snapshot_json;
