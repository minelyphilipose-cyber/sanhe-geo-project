ALTER TABLE presale_report_export
    MODIFY COLUMN worker_id VARCHAR(128) NULL COMMENT '抢占任务的 worker 标识';
