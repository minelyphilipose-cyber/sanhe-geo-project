ALTER TABLE local_agent_session DROP FOREIGN KEY fk_local_agent_session_brand;

ALTER TABLE local_agent_session MODIFY brand_id BIGINT NULL;

ALTER TABLE local_agent_session
  ADD CONSTRAINT fk_local_agent_session_brand FOREIGN KEY (brand_id) REFERENCES brand(id);

CREATE INDEX idx_local_agent_operator_active_seen
  ON local_agent_session (operator_id, status, last_seen_at, updated_at);
