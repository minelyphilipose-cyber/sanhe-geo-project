ALTER TABLE geo_question_workorder
    ADD COLUMN partner_review_status VARCHAR(32) NOT NULL DEFAULT 'inputting' COMMENT '合伙人复核状态' AFTER status,
    ADD COLUMN partner_review_return_reason VARCHAR(500) NULL COMMENT '合伙人复核退回原因' AFTER partner_review_status,
    ADD COLUMN partner_review_submitted_at DATETIME NULL COMMENT '提交负责人复核时间' AFTER partner_review_return_reason,
    ADD COLUMN partner_review_returned_at DATETIME NULL COMMENT '负责人退回时间' AFTER partner_review_submitted_at,
    ADD COLUMN partner_review_hq_submitted_at DATETIME NULL COMMENT '提交总部时间' AFTER partner_review_returned_at,
    ADD COLUMN partner_review_updated_at DATETIME NULL COMMENT '合伙人复核状态更新时间' AFTER partner_review_hq_submitted_at;

UPDATE geo_question_workorder
SET partner_review_status = 'inputting',
    partner_review_updated_at = COALESCE(updated_at, created_at, NOW())
WHERE partner_review_status IS NULL
   OR partner_review_status = '';

CREATE INDEX idx_geo_question_workorder_partner_review
    ON geo_question_workorder (project_id, partner_review_status);
