-- Environment/profile execution lock for self-media schedule workers.
-- Real platform adapters must not execute two tasks against the same AdsPower profile concurrently.

CREATE TABLE IF NOT EXISTS self_media_publish_schedule_environment_lock (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  browser_environment_id BIGINT UNSIGNED NOT NULL,
  schedule_id BIGINT UNSIGNED NOT NULL,
  locked_until DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_self_media_schedule_env_lock (browser_environment_id),
  KEY idx_self_media_schedule_env_lock_schedule (schedule_id),
  KEY idx_self_media_schedule_env_lock_expiry (locked_until),
  CONSTRAINT fk_self_media_schedule_env_lock_environment FOREIGN KEY (browser_environment_id) REFERENCES browser_environment(id),
  CONSTRAINT fk_self_media_schedule_env_lock_schedule FOREIGN KEY (schedule_id) REFERENCES self_media_publish_schedule(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='self-media publish schedule environment execution locks';
