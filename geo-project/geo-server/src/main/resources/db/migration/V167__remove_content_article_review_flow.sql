UPDATE article_draft
SET status = 'approved'
WHERE status IN ('pending_review', 'under_revision', 'rejected');

ALTER TABLE article_draft
    MODIFY status VARCHAR(32) NOT NULL DEFAULT 'approved'
        COMMENT 'approved/distributing/distributed/published/unpublished/deleted';
