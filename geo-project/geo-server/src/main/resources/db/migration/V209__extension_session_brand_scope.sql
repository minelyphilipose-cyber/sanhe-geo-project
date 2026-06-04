ALTER TABLE extension_session
  ADD COLUMN brand_id BIGINT NULL AFTER id,
  ADD KEY idx_extension_brand_status (brand_id, status, last_seen_at),
  ADD CONSTRAINT fk_extension_session_brand FOREIGN KEY (brand_id) REFERENCES brand(id);
