-- ============================================================
-- V241: reassert final V228 checksum contract for article
-- generation summary after V240 was already applied locally.
--
-- Idempotent for databases that already ran final V228 or whose
-- V235/V240 path already repaired this column.
-- ============================================================

ALTER TABLE article_generation_daily_summary
  MODIFY source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 checksum of ordered article rows used by recompute';
