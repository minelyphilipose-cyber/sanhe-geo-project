package com.huanjing.geo.module.retention.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PollRetentionSliceLockService {

    private static final int LOCK_WAIT_TIMEOUT_SECONDS = 1;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the slice fence when needed and acquires its exclusive row lock in one statement.
     * The caller must keep a transaction open for the duration of the protected operation.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockSlice(Long projectId, LocalDate batchDate, String questionTier) {
        if (projectId == null || batchDate == null || questionTier == null || questionTier.isBlank()) {
            throw new IllegalArgumentException("poll retention slice identity is incomplete");
        }
        Integer originalLockWaitSeconds = jdbcTemplate.queryForObject(
                "SELECT @@SESSION.innodb_lock_wait_timeout", Integer.class);
        if (originalLockWaitSeconds == null) {
            throw new IllegalStateException("Failed to read session innodb_lock_wait_timeout");
        }
        jdbcTemplate.execute("SET SESSION innodb_lock_wait_timeout = " + LOCK_WAIT_TIMEOUT_SECONDS);
        RuntimeException lockFailure = null;
        try {
            jdbcTemplate.update("""
                    INSERT INTO data_retention_recompute_slice_lock (
                      domain, project_id, batch_date, question_tier
                    ) VALUES ('poll_results', ?, ?, ?)
                    ON DUPLICATE KEY UPDATE id = id
                    """, projectId, Date.valueOf(batchDate), questionTier.trim().toUpperCase());
        } catch (RuntimeException ex) {
            lockFailure = ex;
            throw ex;
        } finally {
            resetLockWaitTimeout(originalLockWaitSeconds, lockFailure);
        }
    }

    private void resetLockWaitTimeout(int originalLockWaitSeconds, RuntimeException lockFailure) {
        try {
            jdbcTemplate.execute("SET SESSION innodb_lock_wait_timeout = " + originalLockWaitSeconds);
        } catch (RuntimeException resetFailure) {
            if (lockFailure != null) {
                lockFailure.addSuppressed(resetFailure);
                return;
            }
            throw resetFailure;
        }
    }
}
