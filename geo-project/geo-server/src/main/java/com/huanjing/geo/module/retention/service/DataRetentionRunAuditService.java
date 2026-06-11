package com.huanjing.geo.module.retention.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataRetentionRunAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public Long startRun(String domain, String mode, LocalDate windowStart, LocalDate windowEnd, Map<String, Object> metrics) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO data_retention_run (
                      domain, mode, status, retention_window_start, retention_window_end,
                      candidate_count, affected_count, skipped_count, warning_count, metrics_json, started_at
                    ) VALUES (
                      ?, ?, 'running', ?, ?, 0, 0, 0, 0, ?, CURRENT_TIMESTAMP
                    )
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, domain);
            ps.setString(2, mode);
            if (windowStart == null) {
                ps.setNull(3, java.sql.Types.DATE);
            } else {
                ps.setDate(3, Date.valueOf(windowStart));
            }
            if (windowEnd == null) {
                ps.setNull(4, java.sql.Types.DATE);
            } else {
                ps.setDate(4, Date.valueOf(windowEnd));
            }
            ps.setString(5, toJson(metrics));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(500, "Failed to create data retention run");
        }
        return key.longValue();
    }

    public void finishRun(Long runId,
                          String status,
                          long candidateCount,
                          long affectedCount,
                          long skippedCount,
                          long warningCount,
                          Map<String, Object> metrics,
                          String errorMessage) {
        jdbcTemplate.update("""
                UPDATE data_retention_run
                   SET status = ?,
                       candidate_count = ?,
                       affected_count = ?,
                       skipped_count = ?,
                       warning_count = ?,
                       metrics_json = ?,
                       error_message = ?,
                       finished_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """,
                status,
                candidateCount,
                affectedCount,
                skippedCount,
                warningCount,
                toJson(metrics),
                trimError(errorMessage),
                runId);
    }

    private String toJson(Map<String, Object> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (JsonProcessingException ex) {
            throw new BizException("Failed to serialize data retention metrics", ex);
        }
    }

    private String trimError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
