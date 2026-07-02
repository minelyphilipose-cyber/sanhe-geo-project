-- ============================================================
-- V284: partner account voucher references as attachment JSON
-- ============================================================

ALTER TABLE partner_account_txn
    MODIFY COLUMN offline_reference TEXT NULL COMMENT 'offline voucher attachment json';

ALTER TABLE partner_recharge_order
    MODIFY COLUMN offline_reference TEXT NULL COMMENT 'offline voucher attachment json';
