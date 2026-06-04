ALTER TABLE brand
    ADD COLUMN self_media_publish_location_name VARCHAR(64) NULL COMMENT '自媒体发布默认位置' AFTER public_address;
