-- A brand permanently owns one fingerprint-browser environment. Preserve the environment that
-- current code already treats as the default (latest updated row), move active account bindings
-- to it, then soft-delete older duplicates before adding the active-only unique constraint.

CREATE TEMPORARY TABLE tmp_brand_primary_browser_environment (
  brand_id               BIGINT NOT NULL PRIMARY KEY,
  browser_environment_id BIGINT UNSIGNED NOT NULL
) ENGINE=InnoDB;

INSERT INTO tmp_brand_primary_browser_environment (brand_id, browser_environment_id)
SELECT ranked.brand_id, ranked.id
FROM (
  SELECT environment.id,
         environment.brand_id,
         ROW_NUMBER() OVER (
           PARTITION BY environment.brand_id
           ORDER BY environment.updated_at DESC, environment.id DESC
         ) AS row_no
  FROM browser_environment environment
  WHERE environment.deleted_at IS NULL
) ranked
WHERE ranked.row_no = 1;

-- Only accounts that were actually attached to a duplicate environment lose their current
-- runtime-login assertion. Keep the last successful login timestamp as history, but record a
-- newer explicit failed check so the old physical environment is never treated as still valid.
UPDATE self_media_account self_media
JOIN browser_environment_account account_binding
  ON account_binding.self_media_account_id = self_media.id
 AND account_binding.deleted_at IS NULL
JOIN browser_environment duplicate_environment
  ON duplicate_environment.id = account_binding.browser_environment_id
 AND duplicate_environment.deleted_at IS NULL
JOIN tmp_brand_primary_browser_environment primary_environment
  ON primary_environment.brand_id = duplicate_environment.brand_id
SET self_media.last_login_verification_checked_at = CURRENT_TIMESTAMP,
    self_media.last_login_verification_result = 'failed',
    self_media.last_login_verification_method = 'browser_binding_invalidated',
    self_media.last_login_verification_warning = '历史重复环境已合并，请在品牌唯一指纹浏览器环境中重新验证登录状态',
    self_media.updated_at = CURRENT_TIMESTAMP
WHERE account_binding.browser_environment_id <> primary_environment.browser_environment_id;

UPDATE browser_environment_account account_binding
JOIN browser_environment duplicate_environment
  ON duplicate_environment.id = account_binding.browser_environment_id
 AND duplicate_environment.deleted_at IS NULL
JOIN tmp_brand_primary_browser_environment primary_environment
  ON primary_environment.brand_id = duplicate_environment.brand_id
SET account_binding.login_status = 'login_required',
    account_binding.last_verified_at = NULL,
    account_binding.last_login_seen_at = NULL,
    account_binding.last_error_code = 'ENVIRONMENT_MERGED_RELOGIN_REQUIRED',
    account_binding.last_error_message = '历史重复环境已合并，请在品牌唯一环境中重新验证登录状态',
    account_binding.updated_at = CURRENT_TIMESTAMP
WHERE account_binding.deleted_at IS NULL
  AND account_binding.browser_environment_id <> primary_environment.browser_environment_id;

UPDATE browser_environment_account account_binding
JOIN browser_environment duplicate_environment
  ON duplicate_environment.id = account_binding.browser_environment_id
 AND duplicate_environment.deleted_at IS NULL
JOIN tmp_brand_primary_browser_environment primary_environment
  ON primary_environment.brand_id = duplicate_environment.brand_id
SET account_binding.browser_environment_id = primary_environment.browser_environment_id,
    account_binding.updated_at = CURRENT_TIMESTAMP
WHERE account_binding.deleted_at IS NULL
  AND account_binding.browser_environment_id <> primary_environment.browser_environment_id;

UPDATE browser_environment_agent_binding agent_binding
JOIN browser_environment duplicate_environment
  ON duplicate_environment.id = agent_binding.browser_environment_id
 AND duplicate_environment.deleted_at IS NULL
JOIN tmp_brand_primary_browser_environment primary_environment
  ON primary_environment.brand_id = duplicate_environment.brand_id
SET agent_binding.status = 'disabled',
    agent_binding.bound_session_id = NULL,
    agent_binding.binding_version = agent_binding.binding_version + 1,
    agent_binding.updated_at = CURRENT_TIMESTAMP
WHERE agent_binding.browser_environment_id <> primary_environment.browser_environment_id;

UPDATE browser_environment environment
JOIN tmp_brand_primary_browser_environment primary_environment
  ON primary_environment.brand_id = environment.brand_id
SET environment.status = 'deleted',
    environment.deleted_at = CURRENT_TIMESTAMP,
    environment.last_error_code = 'MERGED_INTO_BRAND_PRIMARY_ENVIRONMENT',
    environment.last_error_message = CONCAT('merged into browser environment ', primary_environment.browser_environment_id),
    environment.updated_at = CURRENT_TIMESTAMP
WHERE environment.deleted_at IS NULL
  AND environment.id <> primary_environment.browser_environment_id;

DROP TEMPORARY TABLE tmp_brand_primary_browser_environment;

ALTER TABLE browser_environment
  ADD UNIQUE KEY uk_browser_env_brand_active (brand_id, active_unique_key);
