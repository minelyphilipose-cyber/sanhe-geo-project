ALTER TABLE article_draft
    MODIFY COLUMN batch_id BIGINT UNSIGNED NULL COMMENT 'article generation batch, null for manual articles';
