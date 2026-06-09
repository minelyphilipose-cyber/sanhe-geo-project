CREATE TABLE wechat_callback_event (
  id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  callback_type      VARCHAR(32)   NOT NULL COMMENT 'component_event/authorizer_message',
  component_appid    VARCHAR(64)   NULL,
  authorizer_appid   VARCHAR(64)   NULL,
  event_type         VARCHAR(64)   NULL COMMENT 'InfoType or Event',
  msg_type           VARCHAR(32)   NULL,
  openid             VARCHAR(128)  NULL,
  raw_xml            MEDIUMTEXT    NULL,
  decrypted_xml      MEDIUMTEXT    NULL,
  response_body      MEDIUMTEXT    NULL,
  process_status     VARCHAR(32)   NOT NULL DEFAULT 'success',
  process_error      VARCHAR(1000) NULL,
  received_at        DATETIME      NOT NULL,
  processed_at       DATETIME      NULL,
  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_wechat_callback_type_time (callback_type, received_at),
  KEY idx_wechat_callback_authorizer_time (authorizer_appid, received_at),
  KEY idx_wechat_callback_event_type (event_type, process_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='WeChat Open Platform callback audit events';
