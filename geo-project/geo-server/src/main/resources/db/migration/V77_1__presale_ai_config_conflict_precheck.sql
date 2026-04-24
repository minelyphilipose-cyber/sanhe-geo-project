-- V77_1 · 预检 ai_platform_config 与 presale_platform_config 的关键字段冲突
-- 仅门禁,不改业务表结构/数据,失败即安全回退
-- 门禁字段: api_url / primary_key_ref
--
-- 若本 migration SIGNAL 触发,用以下 SQL 排查冲突行:
-- SELECT platform_code,
--        a.api_url          AS ai_url,
--        p.api_url          AS presale_url,
--        a.primary_key_ref  AS ai_pkr,
--        p.primary_key_ref  AS presale_pkr
-- FROM presale_platform_config p
-- JOIN ai_platform_config a USING(platform_code);

DROP PROCEDURE IF EXISTS pr3f_precheck_conflict;
DELIMITER $$

CREATE PROCEDURE pr3f_precheck_conflict()
BEGIN
  DECLARE v_conflicts INT DEFAULT 0;

  SELECT COUNT(1) INTO v_conflicts
  FROM presale_platform_config p
  JOIN ai_platform_config a ON a.platform_code = p.platform_code
  WHERE
    (
      NULLIF(TRIM(a.api_url), '') IS NOT NULL
      AND NULLIF(TRIM(p.api_url), '') IS NOT NULL
      AND a.api_url <> p.api_url
    )
    OR
    (
      NULLIF(TRIM(a.primary_key_ref), '') IS NOT NULL
      AND NULLIF(TRIM(p.primary_key_ref), '') IS NOT NULL
      AND a.primary_key_ref <> p.primary_key_ref
    );

  IF v_conflicts > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'PR-3.F V77_1 blocked: api_url/primary_key_ref conflicts. See file header for debug SQL.';
  END IF;
END$$

DELIMITER ;

CALL pr3f_precheck_conflict();
DROP PROCEDURE pr3f_precheck_conflict;
