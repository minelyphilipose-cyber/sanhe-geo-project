package com.huanjing.geo.module.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.report.dto.ReportPeriodFreezeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPeriodFreezeService {

    private static final String REPORT_TYPE_QUARTERLY = "quarterly";
    private static final String STATUS_CREATING = "CREATING";
    private static final String STATUS_FROZEN = "FROZEN";
    private static final String STATUS_FAILED = "FAILED";
    private static final String NULL_SENTINEL = "<NULL>";
    private static final String FIELD_SEPARATOR = "\u001F";
    private static final String RECORD_SEPARATOR = "\u001E";
    private static final Duration GUARD_TTL = Duration.ofMinutes(30);
    private static final List<String> POLL_DETAIL_REPORT_TYPES = List.of(REPORT_TYPE_QUARTERLY);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectStorageService objectStorageService;
    private final PlatformTransactionManager transactionManager;

    public ReportPeriodFreezeResponse freezeQuarter(Long projectId, String periodKey, boolean forceRegenerate) {
        if (projectId == null) {
            throw new BizException(400, "projectId is required");
        }
        QuarterPeriod period = parseQuarter(periodKey);
        if (!isPollPeriodClosed(projectId, period.start(), period.end())) {
            return skipped(projectId, REPORT_TYPE_QUARTERLY, period, "period_not_closed");
        }

        String owner = UUID.randomUUID().toString();
        GuardResult guard = acquireGuard(projectId, REPORT_TYPE_QUARTERLY, period.key(), owner);
        if (!guard.acquired()) {
            return skipped(projectId, REPORT_TYPE_QUARTERLY, period, "freeze_guard_busy");
        }

        try {
            return freezeUnderGuard(projectId, REPORT_TYPE_QUARTERLY, period, forceRegenerate, owner);
        } finally {
            releaseGuard(projectId, REPORT_TYPE_QUARTERLY, period.key(), owner);
        }
    }

    public List<String> missingPollDetailFreezeTypes(Long projectId, LocalDate batchDate) {
        List<String> missing = new ArrayList<>();
        for (String reportType : POLL_DETAIL_REPORT_TYPES) {
            QuarterPeriod period = quarterOf(batchDate);
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                      FROM report_period_freeze
                     WHERE project_id = ?
                       AND report_type = ?
                       AND period_key = ?
                       AND status = 'FROZEN'
                    """, Integer.class, projectId, reportType, period.key());
            if (count == null || count == 0) {
                missing.add(reportType + ":" + period.key());
            }
        }
        return missing;
    }

    public int freezePreviousQuarterCandidates(int limit) {
        QuarterPeriod previous = previousQuarter(LocalDate.now());
        List<Long> projectIds = jdbcTemplate.query("""
                SELECT DISTINCT pr.project_id
                  FROM poll_results pr
                 WHERE pr.batch_date >= ?
                   AND pr.batch_date <= ?
                   AND NOT EXISTS (
                         SELECT 1
                           FROM report_period_freeze f
                          WHERE f.project_id = pr.project_id
                            AND f.report_type = 'quarterly'
                            AND f.period_key = ?
                            AND f.status = 'FROZEN'
                       )
                 ORDER BY project_id ASC
                 LIMIT ?
                """, (rs, rowNum) -> rs.getLong("project_id"),
                Date.valueOf(previous.start()), Date.valueOf(previous.end()), previous.key(), Math.max(1, limit));
        int frozen = 0;
        for (Long projectId : projectIds) {
            try {
                ReportPeriodFreezeResponse response = freezeQuarter(projectId, previous.key(), false);
                if (STATUS_FROZEN.equals(response.getStatus())) {
                    frozen++;
                }
            } catch (Exception ex) {
                log.warn("Quarterly report freeze failed, projectId={}, periodKey={}", projectId, previous.key(), ex);
            }
        }
        return frozen;
    }

    private ReportPeriodFreezeResponse freezeUnderGuard(Long projectId,
                                                        String reportType,
                                                        QuarterPeriod period,
                                                        boolean forceRegenerate,
                                                        String owner) {
        List<FreezeDetailRow> rows = loadLatestDetailRows(projectId, period.start(), period.end());
        String sourceChecksum = sourceChecksum(rows);
        FreezeTicket ticket = acquireFreezeTicket(projectId, reportType, period, sourceChecksum, rows.size(), forceRegenerate, owner);
        if (ticket.noop()) {
            return fromTicket(ticket, STATUS_FROZEN, "source_unchanged");
        }

        try {
            byte[] snapshotBytes = snapshotBytes(projectId, reportType, period, ticket, rows);
            String objectChecksum = sha256Hex(snapshotBytes);
            String objectKey = objectKey(projectId, reportType, period.key(), ticket.versionNo());
            objectStorageService.putBytes(objectKey, snapshotBytes, "application/json; charset=utf-8");
            byte[] readBack = objectStorageService.readBytes(objectKey);
            String readBackChecksum = sha256Hex(readBack);
            if (!Objects.equals(objectChecksum, readBackChecksum)) {
                throw new BizException(500, "Freeze object checksum mismatch after readback");
            }
            ObjectStorageService.ObjectStat stat = objectStorageService.stat(objectKey);
            markFrozen(ticket.freezeId(), owner, objectKey, objectChecksum, stat.size(), rows.size());
            ReportPeriodFreezeResponse response = fromTicket(ticket, STATUS_FROZEN, null);
            response.setSnapshotObjectKey(objectKey);
            response.setObjectChecksum(objectChecksum);
            response.setObjectSizeBytes(stat.size());
            return response;
        } catch (Exception ex) {
            markFailed(ticket.freezeId(), owner, ex);
            throw ex;
        }
    }

    private List<FreezeDetailRow> loadLatestDetailRows(Long projectId, LocalDate start, LocalDate end) {
        return jdbcTemplate.query("""
                SELECT *
                  FROM (
                        SELECT pr.id,
                               pr.project_id,
                               pr.keyword_result_id,
                               pr.keyword_text_snapshot,
                               CASE
                                 WHEN pr.keyword_result_id IS NOT NULL THEN 'ID'
                                 ELSE 'TEXT'
                               END AS keyword_identity_type,
                               CASE
                                 WHEN pr.keyword_result_id IS NOT NULL THEN CONCAT('ID:', pr.keyword_result_id)
                                 ELSE CONCAT('TEXT:', LOWER(TRIM(COALESCE(pr.keyword_text_snapshot, ''))))
                               END AS keyword_identity_value,
                               LOWER(TRIM(COALESCE(pr.keyword_text_snapshot, ''))) AS keyword_text_normalized,
                               pr.question_tier,
                               pr.platform_id,
                               pr.platform_code,
                               ap.platform_name AS platform_name_snapshot,
                               pr.batch_date,
                               pr.status,
                               pr.request_count,
                               pr.response_time_ms,
                               pr.is_hit,
                               pr.effective_hit,
                               pr.match_type,
                               pr.site_mentioned,
                               pr.contact_mentioned,
                               pr.contact_mention_count,
                               pr.judge_status,
                               pr.hit_level,
                               pr.hit_sentiment,
                               pr.mention_type,
                               pr.judge_evidence,
                               pr.judge_risk_reason,
                               pr.judge_model,
                               pr.judge_at,
                               pr.judge_error,
                               pr.record_type,
                               pr.detail_json,
                               pr.created_at,
                               pr.updated_at,
                               ROW_NUMBER() OVER (
                                 PARTITION BY pr.question_tier,
                                              CASE
                                                WHEN pr.keyword_result_id IS NOT NULL THEN CONCAT('ID:', pr.keyword_result_id)
                                                ELSE CONCAT('TEXT:', LOWER(TRIM(COALESCE(pr.keyword_text_snapshot, ''))))
                                              END,
                                              pr.platform_id
                                 ORDER BY COALESCE(pr.updated_at, pr.created_at) DESC, pr.id DESC
                               ) AS rn
                          FROM poll_results pr
                          LEFT JOIN ai_platform_config ap ON ap.id = pr.platform_id
                         WHERE pr.project_id = ?
                           AND pr.batch_date >= ?
                           AND pr.batch_date <= ?
                       ) ranked
                 WHERE ranked.rn = 1
                 ORDER BY ranked.question_tier ASC, ranked.keyword_identity_value ASC, ranked.platform_id ASC
                """, (rs, rowNum) -> mapFreezeDetailRow(rs),
                projectId, Date.valueOf(start), Date.valueOf(end));
    }

    private FreezeDetailRow mapFreezeDetailRow(ResultSet rs) throws SQLException {
        return new FreezeDetailRow(
                rs.getLong("id"),
                rs.getLong("project_id"),
                nullableLong(rs, "keyword_result_id"),
                rs.getString("keyword_text_snapshot"),
                rs.getString("keyword_identity_type"),
                rs.getString("keyword_identity_value"),
                rs.getString("keyword_text_normalized"),
                rs.getString("question_tier"),
                rs.getLong("platform_id"),
                rs.getString("platform_code"),
                rs.getString("platform_name_snapshot"),
                rs.getDate("batch_date").toLocalDate(),
                rs.getString("status"),
                nullableInt(rs, "request_count"),
                nullableLong(rs, "response_time_ms"),
                nullableBoolean(rs, "is_hit"),
                nullableBoolean(rs, "effective_hit"),
                rs.getString("match_type"),
                nullableBoolean(rs, "site_mentioned"),
                nullableBoolean(rs, "contact_mentioned"),
                nullableInt(rs, "contact_mention_count"),
                rs.getString("judge_status"),
                rs.getString("hit_level"),
                rs.getString("hit_sentiment"),
                rs.getString("mention_type"),
                rs.getString("judge_evidence"),
                rs.getString("judge_risk_reason"),
                rs.getString("judge_model"),
                nullableDateTime(rs, "judge_at"),
                rs.getString("judge_error"),
                rs.getString("record_type"),
                rs.getString("detail_json"),
                nullableDateTime(rs, "created_at"),
                nullableDateTime(rs, "updated_at")
        );
    }

    private FreezeTicket acquireFreezeTicket(Long projectId,
                                             String reportType,
                                             QuarterPeriod period,
                                             String sourceChecksum,
                                             int rowCount,
                                             boolean forceRegenerate,
                                             String owner) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            List<FreezeRow> rows = jdbcTemplate.query("""
                    SELECT id, version_no, status, source_checksum
                      FROM report_period_freeze
                     WHERE project_id = ?
                       AND report_type = ?
                       AND period_key = ?
                     ORDER BY version_no DESC
                     LIMIT 1
                     FOR UPDATE
                    """, (rs, rowNum) -> new FreezeRow(
                            rs.getLong("id"),
                            rs.getInt("version_no"),
                            rs.getString("status"),
                            rs.getString("source_checksum")
                    ), projectId, reportType, period.key());

            FreezeRow latest = rows.isEmpty() ? null : rows.get(0);
            if (latest != null && STATUS_FROZEN.equals(latest.status())
                    && Objects.equals(latest.sourceChecksum(), sourceChecksum) && !forceRegenerate) {
                return new FreezeTicket(latest.id(), latest.versionNo(), projectId, reportType, period,
                        sourceChecksum, rowCount, true);
            }

            if (latest != null && !STATUS_FROZEN.equals(latest.status())) {
                jdbcTemplate.update("""
                        UPDATE report_period_freeze
                           SET status = 'CREATING',
                               source_checksum = ?,
                               source_row_count = ?,
                               metrics_json = ?,
                               lock_owner = ?,
                               lock_expires_at = ?,
                               freeze_started_at = CURRENT_TIMESTAMP,
                               failed_at = NULL,
                               error_message = NULL
                         WHERE id = ?
                        """, sourceChecksum, rowCount, metricsJson(rowCount, forceRegenerate), owner,
                        Timestamp.valueOf(LocalDateTime.now().plus(GUARD_TTL)), latest.id());
                return new FreezeTicket(latest.id(), latest.versionNo(), projectId, reportType, period,
                        sourceChecksum, rowCount, false);
            }

            int versionNo = latest == null ? 1 : latest.versionNo() + 1;
            Long freezeId = insertFreezeRow(projectId, reportType, period, versionNo, sourceChecksum, rowCount, forceRegenerate, owner);
            return new FreezeTicket(freezeId, versionNo, projectId, reportType, period, sourceChecksum, rowCount, false);
        });
    }

    private Long insertFreezeRow(Long projectId,
                                 String reportType,
                                 QuarterPeriod period,
                                 int versionNo,
                                 String sourceChecksum,
                                 int rowCount,
                                 boolean forceRegenerate,
                                 String owner) {
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO report_period_freeze (
                      project_id, report_type, period_key, period_start, period_end, version_no,
                      status, source_checksum, source_row_count, metrics_json,
                      lock_owner, lock_expires_at, freeze_started_at
                    ) VALUES (?, ?, ?, ?, ?, ?, 'CREATING', ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, projectId);
            ps.setString(2, reportType);
            ps.setString(3, period.key());
            ps.setDate(4, Date.valueOf(period.start()));
            ps.setDate(5, Date.valueOf(period.end()));
            ps.setInt(6, versionNo);
            ps.setString(7, sourceChecksum);
            ps.setInt(8, rowCount);
            ps.setString(9, metricsJson(rowCount, forceRegenerate));
            ps.setString(10, owner);
            ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now().plus(GUARD_TTL)));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(500, "Failed to create report period freeze row");
        }
        return key.longValue();
    }

    private GuardResult acquireGuard(Long projectId, String reportType, String periodKey, String owner) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            jdbcTemplate.update("""
                    INSERT IGNORE INTO report_period_freeze_guard (
                      project_id, report_type, period_key
                    ) VALUES (?, ?, ?)
                    """, projectId, reportType, periodKey);
            List<GuardRow> rows = jdbcTemplate.query("""
                    SELECT id, lock_owner, lock_expires_at
                      FROM report_period_freeze_guard
                     WHERE project_id = ?
                       AND report_type = ?
                       AND period_key = ?
                     FOR UPDATE
                    """, (rs, rowNum) -> new GuardRow(
                            rs.getLong("id"),
                            rs.getString("lock_owner"),
                            nullableDateTime(rs, "lock_expires_at")
                    ), projectId, reportType, periodKey);
            if (rows.isEmpty()) {
                throw new BizException(500, "Report freeze guard row not found");
            }
            GuardRow row = rows.get(0);
            LocalDateTime now = LocalDateTime.now();
            if (row.lockExpiresAt() != null && row.lockExpiresAt().isAfter(now)
                    && !Objects.equals(owner, row.lockOwner())) {
                return new GuardResult(false);
            }
            jdbcTemplate.update("""
                    UPDATE report_period_freeze_guard
                       SET lock_owner = ?,
                           lock_expires_at = ?,
                           acquired_at = CURRENT_TIMESTAMP
                     WHERE id = ?
                    """, owner, Timestamp.valueOf(now.plus(GUARD_TTL)), row.id());
            return new GuardResult(true);
        });
    }

    private void releaseGuard(Long projectId, String reportType, String periodKey, String owner) {
        jdbcTemplate.update("""
                UPDATE report_period_freeze_guard
                   SET lock_expires_at = CURRENT_TIMESTAMP
                 WHERE project_id = ?
                   AND report_type = ?
                   AND period_key = ?
                   AND lock_owner = ?
                """, projectId, reportType, periodKey, owner);
    }

    private void markFrozen(Long freezeId, String owner, String objectKey, String objectChecksum, long objectSize, int rowCount) {
        int updated = jdbcTemplate.update("""
                UPDATE report_period_freeze
                   SET status = 'FROZEN',
                       snapshot_object_key = ?,
                       object_checksum = ?,
                       object_size_bytes = ?,
                       source_row_count = ?,
                       frozen_at = CURRENT_TIMESTAMP,
                       lock_expires_at = CURRENT_TIMESTAMP,
                       error_message = NULL
                 WHERE id = ?
                   AND status = 'CREATING'
                   AND lock_owner = ?
                """, objectKey, objectChecksum, objectSize, rowCount, freezeId, owner);
        if (updated != 1) {
            throw new BizException(409, "Report freeze row lock lost before marking FROZEN");
        }
    }

    private void markFailed(Long freezeId, String owner, Exception ex) {
        jdbcTemplate.update("""
                UPDATE report_period_freeze
                   SET status = 'FAILED',
                       failed_at = CURRENT_TIMESTAMP,
                       lock_expires_at = CURRENT_TIMESTAMP,
                       error_message = ?
                 WHERE id = ?
                   AND lock_owner = ?
                """, trimError(ex.getMessage()), freezeId, owner);
    }

    private boolean isPollPeriodClosed(Long projectId, LocalDate start, LocalDate end) {
        Integer open = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM poll_batches
                 WHERE project_id = ?
                   AND batch_date >= ?
                   AND batch_date <= ?
                   AND status NOT IN ('finished', 'finished_with_failures', 'failed')
                """, Integer.class, projectId, Date.valueOf(start), Date.valueOf(end));
        return open == null || open == 0;
    }

    private byte[] snapshotBytes(Long projectId,
                                 String reportType,
                                 QuarterPeriod period,
                                 FreezeTicket ticket,
                                 List<FreezeDetailRow> rows) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", 1);
        root.put("project_id", projectId);
        root.put("report_type", reportType);
        root.put("period_key", period.key());
        root.put("period_start", period.start());
        root.put("period_end", period.end());
        root.put("version_no", ticket.versionNo());
        root.put("source_checksum", ticket.sourceChecksum());
        root.put("source_row_count", rows.size());
        root.put("frozen_at", LocalDateTime.now());
        root.put("rows", rows.stream().map(FreezeDetailRow::toMap).collect(Collectors.toList()));
        try {
            return objectMapper.writeValueAsBytes(root);
        } catch (JsonProcessingException ex) {
            throw new BizException("Failed to serialize report period freeze snapshot", ex);
        }
    }

    private String sourceChecksum(List<FreezeDetailRow> rows) {
        return sha256Hex(rows.stream()
                .sorted(Comparator.comparing(FreezeDetailRow::questionTier)
                        .thenComparing(FreezeDetailRow::keywordIdentityValue)
                        .thenComparing(FreezeDetailRow::platformId))
                .map(FreezeDetailRow::canonical)
                .collect(Collectors.joining(RECORD_SEPARATOR)));
    }

    private String objectKey(Long projectId, String reportType, String periodKey, int versionNo) {
        return "retention/freeze/report-period/project-" + projectId + "/" + reportType + "/"
                + periodKey + "/v" + versionNo + ".json";
    }

    private ReportPeriodFreezeResponse skipped(Long projectId, String reportType, QuarterPeriod period, String reason) {
        ReportPeriodFreezeResponse response = new ReportPeriodFreezeResponse();
        response.setProjectId(projectId);
        response.setReportType(reportType);
        response.setPeriodKey(period.key());
        response.setPeriodStart(period.start());
        response.setPeriodEnd(period.end());
        response.setStatus("SKIPPED");
        response.setReason(reason);
        return response;
    }

    private ReportPeriodFreezeResponse fromTicket(FreezeTicket ticket, String status, String reason) {
        ReportPeriodFreezeResponse response = new ReportPeriodFreezeResponse();
        response.setFreezeId(ticket.freezeId());
        response.setProjectId(ticket.projectId());
        response.setReportType(ticket.reportType());
        response.setPeriodKey(ticket.period().key());
        response.setPeriodStart(ticket.period().start());
        response.setPeriodEnd(ticket.period().end());
        response.setVersionNo(ticket.versionNo());
        response.setStatus(status);
        response.setReason(reason);
        response.setSourceRowCount(ticket.rowCount());
        response.setSourceChecksum(ticket.sourceChecksum());
        return response;
    }

    private String metricsJson(int rowCount, boolean forceRegenerate) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "sourceRowCount", rowCount,
                    "forceRegenerate", forceRegenerate
            ));
        } catch (JsonProcessingException ex) {
            throw new BizException("Failed to serialize report freeze metrics", ex);
        }
    }

    private QuarterPeriod parseQuarter(String periodKey) {
        if (!StringUtils.hasText(periodKey)) {
            throw new BizException(400, "periodKey is required");
        }
        String normalized = periodKey.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("\\d{4}Q[1-4]")) {
            throw new BizException(400, "periodKey must use format yyyyQn, e.g. 2026Q1");
        }
        int year = Integer.parseInt(normalized.substring(0, 4));
        int quarter = Integer.parseInt(normalized.substring(5));
        LocalDate start = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
        LocalDate end = YearMonth.from(start.plusMonths(2)).atEndOfMonth();
        return new QuarterPeriod(normalized, start, end);
    }

    private QuarterPeriod quarterOf(LocalDate date) {
        int quarter = ((date.getMonthValue() - 1) / 3) + 1;
        return parseQuarter(date.getYear() + "Q" + quarter);
    }

    private QuarterPeriod previousQuarter(LocalDate date) {
        return quarterOf(date.minusMonths(3));
    }

    private static String canonical(Object... fields) {
        StringJoiner joiner = new StringJoiner(FIELD_SEPARATOR);
        for (Object field : fields) {
            if (field == null) {
                joiner.add(NULL_SENTINEL);
            } else {
                joiner.add(String.valueOf(field).trim());
            }
        }
        return joiner.toString();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            StringBuilder out = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static LocalDateTime nullableDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "report period freeze failed";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private record QuarterPeriod(String key, LocalDate start, LocalDate end) {
    }

    private record GuardRow(Long id, String lockOwner, LocalDateTime lockExpiresAt) {
    }

    private record GuardResult(boolean acquired) {
    }

    private record FreezeRow(Long id, Integer versionNo, String status, String sourceChecksum) {
    }

    private record FreezeTicket(Long freezeId,
                                Integer versionNo,
                                Long projectId,
                                String reportType,
                                QuarterPeriod period,
                                String sourceChecksum,
                                Integer rowCount,
                                boolean noop) {
    }

    private record FreezeDetailRow(Long pollResultId,
                                   Long projectId,
                                   Long keywordResultId,
                                   String keywordTextSnapshot,
                                   String keywordIdentityType,
                                   String keywordIdentityValue,
                                   String keywordTextNormalized,
                                   String questionTier,
                                   Long platformId,
                                   String platformCode,
                                   String platformNameSnapshot,
                                   LocalDate batchDate,
                                   String status,
                                   Integer requestCount,
                                   Long responseTimeMs,
                                   Boolean isHit,
                                   Boolean effectiveHit,
                                   String matchType,
                                   Boolean siteMentioned,
                                   Boolean contactMentioned,
                                   Integer contactMentionCount,
                                   String judgeStatus,
                                   String hitLevel,
                                   String hitSentiment,
                                   String mentionType,
                                   String judgeEvidence,
                                   String judgeRiskReason,
                                   String judgeModel,
                                   LocalDateTime judgeAt,
                                   String judgeError,
                                   String recordType,
                                   String detailJson,
                                   LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {

        String canonical() {
            return ReportPeriodFreezeService.canonical(
                    pollResultId, keywordResultId, keywordTextSnapshot, keywordIdentityType, keywordIdentityValue,
                    keywordTextNormalized, questionTier, platformId, platformCode, platformNameSnapshot, batchDate,
                    status, requestCount, responseTimeMs, isHit, effectiveHit, matchType, siteMentioned,
                    contactMentioned, contactMentionCount, judgeStatus, hitLevel, hitSentiment, mentionType,
                    judgeEvidence, judgeRiskReason, judgeModel, judgeAt, judgeError, recordType, detailJson,
                    createdAt, updatedAt
            );
        }

        Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("poll_result_id", pollResultId);
            out.put("project_id", projectId);
            out.put("keyword_result_id", keywordResultId);
            out.put("keyword_text_snapshot", keywordTextSnapshot);
            out.put("keyword_identity_type", keywordIdentityType);
            out.put("keyword_identity_value", keywordIdentityValue);
            out.put("keyword_text_normalized", keywordTextNormalized);
            out.put("question_tier", questionTier);
            out.put("platform_id", platformId);
            out.put("platform_code", platformCode);
            out.put("platform_name_snapshot", platformNameSnapshot);
            out.put("batch_date", batchDate);
            out.put("status", status);
            out.put("request_count", requestCount);
            out.put("response_time_ms", responseTimeMs);
            out.put("is_hit", isHit);
            out.put("effective_hit", effectiveHit);
            out.put("match_type", matchType);
            out.put("site_mentioned", siteMentioned);
            out.put("contact_mentioned", contactMentioned);
            out.put("contact_mention_count", contactMentionCount);
            out.put("judge_status", judgeStatus);
            out.put("hit_level", hitLevel);
            out.put("hit_sentiment", hitSentiment);
            out.put("mention_type", mentionType);
            out.put("judge_evidence", judgeEvidence);
            out.put("judge_risk_reason", judgeRiskReason);
            out.put("judge_model", judgeModel);
            out.put("judge_at", judgeAt);
            out.put("judge_error", judgeError);
            out.put("record_type", recordType);
            out.put("detail_json", detailJson);
            out.put("created_at", createdAt);
            out.put("updated_at", updatedAt);
            return out;
        }
    }
}
