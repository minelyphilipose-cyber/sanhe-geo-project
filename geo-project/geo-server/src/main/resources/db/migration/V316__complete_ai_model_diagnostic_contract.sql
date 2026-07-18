-- Complete the diagnostic persistence contract after the immutable V315 baseline.

ALTER TABLE ai_model_diagnostic_sessions
    ADD UNIQUE KEY uk_ai_diag_session_full_identity (id, operator_id, session_id),
    ADD CONSTRAINT fk_ai_diag_session_operator FOREIGN KEY (operator_id)
        REFERENCES sys_user(id) ON DELETE RESTRICT;

ALTER TABLE ai_model_diagnostic_runs
    ADD COLUMN template_version VARCHAR(64) NULL
        COMMENT 'actual production prompt template version' AFTER probe_version,
    DROP FOREIGN KEY fk_ai_diag_run_session,
    ADD CONSTRAINT fk_ai_diag_run_session_identity
        FOREIGN KEY (session_record_id, operator_id, session_id)
        REFERENCES ai_model_diagnostic_sessions(id, operator_id, session_id) ON DELETE RESTRICT;

ALTER TABLE ai_model_diagnostic_runs
    DROP CHECK chk_ai_diag_run_status_conclusion,
    ADD CONSTRAINT chk_ai_diag_run_status_conclusion CHECK (
        (status = 'SUCCEEDED' AND conclusion IS NOT NULL
            AND conclusion IN ('PASS', 'WARNING', 'FAIL'))
        OR (status = 'FAILED' AND conclusion IS NOT NULL AND conclusion = 'FAIL')
        OR (status IN ('RUNNING', 'REJECTED', 'ABANDONED') AND conclusion IS NULL)
    );
