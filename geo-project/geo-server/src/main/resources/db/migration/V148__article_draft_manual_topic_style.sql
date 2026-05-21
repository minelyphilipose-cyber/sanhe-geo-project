ALTER TABLE article_draft
    ADD COLUMN content_style VARCHAR(32) NULL COMMENT 'manual article platform/content style' AFTER article_type,
    ADD COLUMN topic VARCHAR(1000) NULL COMMENT 'manual article topic' AFTER content_style,
    ADD COLUMN topic_as_question VARCHAR(1000) NULL COMMENT 'manual article source question topic' AFTER topic,
    ADD KEY idx_article_draft_content_style (content_style);
