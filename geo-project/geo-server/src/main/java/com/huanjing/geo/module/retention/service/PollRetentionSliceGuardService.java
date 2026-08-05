package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PollRetentionSliceGuardService {

    private final JdbcTemplate jdbcTemplate;
    private final PollRetentionSliceLockService sliceLockService;

    public void lockAndRequireWritable(PollResult result) {
        if (result == null) {
            throw new IllegalArgumentException("poll result is required");
        }
        lockAndRequireWritable(result.getProjectId(), result.getBatchDate(), result.getQuestionTier());
    }

    public void lockAndRequireWritable(Long projectId, LocalDate batchDate, String questionTier) {
        if (projectId == null || batchDate == null || questionTier == null || questionTier.isBlank()) {
            throw new IllegalArgumentException("poll retention slice identity is incomplete");
        }
        String normalizedTier = questionTier.trim().toUpperCase();
        sliceLockService.lockSlice(projectId, batchDate, normalizedTier);
        Integer purged = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM data_retention_purged_slice
                 WHERE domain = 'poll_results'
                   AND project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                   AND status = 'purged'
                """, Integer.class, projectId, Date.valueOf(batchDate), normalizedTier);
        if (purged != null && purged > 0) {
            throw new BizException(409, "Poll retention slice was already purged");
        }
    }
}
