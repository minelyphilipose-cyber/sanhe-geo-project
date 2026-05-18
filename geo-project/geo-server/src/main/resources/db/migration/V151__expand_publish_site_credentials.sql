-- ============================================================
-- V151: allow large forum account / cookie credentials
-- ============================================================

ALTER TABLE publish_sites
  MODIFY COLUMN api_credential_encrypted MEDIUMTEXT NULL
    COMMENT 'Encrypted platform credential; forum cookie JSON can exceed varchar limits';
