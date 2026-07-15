-- Route local-helper execution by the AdsPower environment that owns the task.
-- created_by remains audit metadata and is intentionally not copied here.

CREATE TABLE IF NOT EXISTS browser_environment_agent_binding (
  browser_environment_id  BIGINT UNSIGNED NOT NULL,
  machine_id               VARCHAR(128) NOT NULL,
  active_profile           VARCHAR(32) NOT NULL,
  bound_session_id         BIGINT NULL,
  status                   VARCHAR(32) NOT NULL DEFAULT 'active',
  binding_version          BIGINT NOT NULL DEFAULT 1,
  bound_by                 BIGINT NULL,
  bound_at                 DATETIME NOT NULL,
  last_verified_at         DATETIME NULL,
  created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (browser_environment_id),
  KEY idx_browser_environment_agent_machine (machine_id, active_profile, status),
  KEY idx_browser_environment_agent_session (bound_session_id, status),
  CONSTRAINT fk_browser_environment_agent_environment
    FOREIGN KEY (browser_environment_id) REFERENCES browser_environment(id),
  CONSTRAINT fk_browser_environment_agent_session
    FOREIGN KEY (bound_session_id) REFERENCES local_agent_session(id),
  CONSTRAINT fk_browser_environment_agent_bound_by
    FOREIGN KEY (bound_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Stable local-helper machine ownership for AdsPower browser environments';

-- Existing environments are backfilled only when active extension/runtime evidence
-- resolves to exactly one helper machine/profile. Ambiguous environments deliberately
-- remain unbound and must be re-imported from the computer that owns them.
INSERT INTO browser_environment_agent_binding (
  browser_environment_id,
  machine_id,
  active_profile,
  bound_session_id,
  status,
  binding_version,
  bound_by,
  bound_at,
  last_verified_at,
  created_at,
  updated_at
)
SELECT ranked.browser_environment_id,
       ranked.machine_id,
       ranked.active_profile,
       ranked.session_id,
       'active',
       1,
       ranked.operator_id,
       CURRENT_TIMESTAMP,
       ranked.last_seen_at,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (
  SELECT be.id AS browser_environment_id,
         lar.machine_id,
         lar.active_profile,
         lar.session_id,
         lar.operator_id,
         lar.last_seen_at,
         ROW_NUMBER() OVER (
           PARTITION BY be.id
           ORDER BY es.last_seen_at DESC,
                    es.bound_at DESC,
                    lar.last_seen_at DESC,
                    lar.updated_at DESC
         ) AS row_no
  FROM browser_environment be
  JOIN extension_session es
    ON es.brand_id = be.brand_id
   AND es.status = 'active'
   AND (
     (es.provider_profile_id IS NOT NULL AND es.provider_profile_id = be.provider_profile_id)
     OR (es.environment_key IS NOT NULL AND es.environment_key = be.environment_key)
   )
  JOIN local_agent_runtime_status lar
    ON lar.operator_id = es.operator_id
   AND lar.session_id IS NOT NULL
  JOIN local_agent_session las
    ON las.id = lar.session_id
   AND las.operator_id = es.operator_id
   AND las.status = 'active'
   AND las.expires_at > CURRENT_TIMESTAMP
  WHERE be.deleted_at IS NULL
    AND be.status = 'active'
) ranked
JOIN (
  SELECT be.id AS browser_environment_id
  FROM browser_environment be
  JOIN extension_session es
    ON es.brand_id = be.brand_id
   AND es.status = 'active'
   AND (
     (es.provider_profile_id IS NOT NULL AND es.provider_profile_id = be.provider_profile_id)
     OR (es.environment_key IS NOT NULL AND es.environment_key = be.environment_key)
   )
  JOIN local_agent_runtime_status lar
    ON lar.operator_id = es.operator_id
   AND lar.session_id IS NOT NULL
  JOIN local_agent_session las
    ON las.id = lar.session_id
   AND las.operator_id = es.operator_id
   AND las.status = 'active'
   AND las.expires_at > CURRENT_TIMESTAMP
  WHERE be.deleted_at IS NULL
    AND be.status = 'active'
  GROUP BY be.id
  HAVING COUNT(DISTINCT lar.machine_id, lar.active_profile) = 1
) unambiguous
  ON unambiguous.browser_environment_id = ranked.browser_environment_id
WHERE ranked.row_no = 1
ON DUPLICATE KEY UPDATE
  machine_id = VALUES(machine_id),
  active_profile = VALUES(active_profile),
  bound_session_id = VALUES(bound_session_id),
  status = 'active',
  binding_version = binding_version + 1,
  bound_by = VALUES(bound_by),
  bound_at = VALUES(bound_at),
  last_verified_at = VALUES(last_verified_at),
  updated_at = VALUES(updated_at);
