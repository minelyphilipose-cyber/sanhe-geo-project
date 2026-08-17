-- Platform account IDs were not historically validated on input and may contain random values.
-- Account name is the authoritative browser-login identity; remove obsolete current-health warnings.
UPDATE self_media_account
SET last_login_verification_warning = NULL
WHERE last_login_verification_result = 'success'
  AND last_login_verification_warning IN (
    '平台账号 ID 与系统记录不同，建议后续核对账号资料',
    '平台账号 ID 与系统记录不同，登录名称已确认一致'
  );

UPDATE self_media_login_verification
SET result_code = 'SUCCESS',
    result_message = '登录状态验证成功'
WHERE status = 'succeeded'
  AND result_code = 'SUCCESS_WITH_ID_WARNING';
