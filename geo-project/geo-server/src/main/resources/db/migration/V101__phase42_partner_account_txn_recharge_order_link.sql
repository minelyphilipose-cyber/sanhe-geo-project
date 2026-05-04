ALTER TABLE partner_account_txn
    ADD COLUMN recharge_order_id BIGINT NULL AFTER related_project_id,
    ADD KEY idx_partner_account_txn_recharge_order (recharge_order_id);

UPDATE partner_account_txn txn
JOIN partner_recharge_order ro ON ro.account_txn_id = txn.id
SET txn.recharge_order_id = ro.id
WHERE txn.recharge_order_id IS NULL;
