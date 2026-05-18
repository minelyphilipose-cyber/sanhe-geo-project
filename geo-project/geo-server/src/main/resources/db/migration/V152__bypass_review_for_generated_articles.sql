UPDATE article_draft ad
SET status = 'approved'
WHERE ad.status = 'pending_review'
  AND EXISTS (
      SELECT 1
      FROM article_draft_version adv
      WHERE adv.article_id = ad.id
        AND LOWER(TRIM(adv.generated_by)) IN ('ai', 'system', 'batch_ai', 'ai_preview')
  );
