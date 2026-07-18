package com.huanjing.geo.module.dispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PollSummaryRecomputeService {

    private static final String NULL_SENTINEL = "<NULL>";
    private static final String FIELD_SEPARATOR = "\u001F";
    private static final String RECORD_SEPARATOR = "\u001E";

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecomputeResult recomputeSlice(Long projectId, LocalDate batchDate, String questionTier) {
        String normalizedTier = normalizeTier(questionTier);
        if (isPurgedSlice(projectId, batchDate, normalizedTier)) {
            return RecomputeResult.skipped(projectId, batchDate, normalizedTier, "purged_slice");
        }

        lockSliceForUpdate(projectId, batchDate, normalizedTier);
        if (isPurgedSlice(projectId, batchDate, normalizedTier)) {
            return RecomputeResult.skipped(projectId, batchDate, normalizedTier, "purged_slice");
        }

        List<PollSourceRow> sourceRows = loadSourceRows(projectId, batchDate, normalizedTier);
        List<KeywordSummaryRow> keywordRows = buildKeywordRows(projectId, batchDate, normalizedTier, sourceRows);
        List<PlatformSummaryRow> platformRows = buildPlatformRows(projectId, batchDate, normalizedTier, sourceRows);

        int keywordUpserts = upsertKeywordRows(keywordRows);
        int platformUpserts = upsertPlatformRows(platformRows);
        int keywordDeleted = deleteKeywordZombies(projectId, batchDate, normalizedTier, keywordRows);
        int platformDeleted = deletePlatformZombies(projectId, batchDate, normalizedTier, platformRows);

        return new RecomputeResult(
                projectId,
                batchDate,
                normalizedTier,
                false,
                null,
                sourceRows.size(),
                keywordRows.size(),
                platformRows.size(),
                keywordUpserts,
                platformUpserts,
                keywordDeleted,
                platformDeleted
        );
    }

    private boolean isPurgedSlice(Long projectId, LocalDate batchDate, String questionTier) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM data_retention_purged_slice
                 WHERE domain = 'poll_results'
                   AND project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                   AND status = 'purged'
                """, Integer.class, projectId, Date.valueOf(batchDate), questionTier);
        return count != null && count > 0;
    }

    private void lockSliceForUpdate(Long projectId, LocalDate batchDate, String questionTier) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO data_retention_recompute_slice_lock (
                  domain, project_id, batch_date, question_tier
                ) VALUES ('poll_results', ?, ?, ?)
                """, projectId, Date.valueOf(batchDate), questionTier);
        Long lockId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM data_retention_recompute_slice_lock
                 WHERE domain = 'poll_results'
                   AND project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                 FOR UPDATE
                """, Long.class, projectId, Date.valueOf(batchDate), questionTier);
        if (lockId == null) {
            throw new IllegalStateException("Poll summary recompute slice lock row not found");
        }
    }

    private List<PollSourceRow> loadSourceRows(Long projectId, LocalDate batchDate, String questionTier) {
        return jdbcTemplate.query("""
                SELECT pr.id,
                       pr.project_id,
                       pr.keyword_result_id,
                       pr.keyword_text_snapshot,
                       pr.question_tier,
                       pr.platform_id,
                       pr.platform_code,
                       pr.channel_code,
                       ap.platform_name AS platform_name_snapshot,
                       pr.status,
                       pr.request_count,
                       pr.response_time_ms,
                       pr.is_hit,
                       pr.effective_hit,
                       pr.site_mentioned,
                       pr.contact_mentioned,
                       pr.contact_mention_count,
                       pr.effective_attempt_id,
                       pr.execution_finalized,
                       pr.search_requested,
                       pr.search_triggered,
                       pr.brand_in_search,
                       pr.brand_in_answer,
                       pr.confirmed_citation_exposure,
                       pr.record_type,
                       pr.created_at,
                       pr.updated_at
                  FROM poll_results pr
                  LEFT JOIN poll_batches pb ON pb.id = pr.batch_id
                  LEFT JOIN ai_platform_config ap ON ap.id = pr.platform_id
                 WHERE pr.project_id = ?
                   AND pr.batch_date = ?
                   AND pr.question_tier = ?
                   AND pr.deleted_at IS NULL
                   AND COALESCE(pb.trigger_type, 'SCHEDULED') != 'MANUAL'
                 ORDER BY pr.id ASC
                """, (rs, rowNum) -> mapSourceRow(rs), projectId, Date.valueOf(batchDate), questionTier);
    }

    private PollSourceRow mapSourceRow(ResultSet rs) throws SQLException {
        return new PollSourceRow(
                rs.getLong("id"),
                rs.getLong("project_id"),
                nullableLong(rs, "keyword_result_id"),
                rs.getString("keyword_text_snapshot"),
                rs.getString("question_tier"),
                nullableLong(rs, "platform_id"),
                rs.getString("platform_code"),
                rs.getString("channel_code"),
                rs.getString("platform_name_snapshot"),
                rs.getString("status"),
                nullableInt(rs, "request_count"),
                nullableLong(rs, "response_time_ms"),
                nullableBoolean(rs, "is_hit"),
                nullableBoolean(rs, "effective_hit"),
                nullableBoolean(rs, "site_mentioned"),
                nullableBoolean(rs, "contact_mentioned"),
                nullableInt(rs, "contact_mention_count"),
                nullableLong(rs, "effective_attempt_id"),
                nullableBoolean(rs, "execution_finalized"),
                nullableBoolean(rs, "search_requested"),
                nullableBoolean(rs, "search_triggered"),
                nullableBoolean(rs, "brand_in_search"),
                nullableBoolean(rs, "brand_in_answer"),
                nullableBoolean(rs, "confirmed_citation_exposure"),
                rs.getString("record_type"),
                nullableDateTime(rs, "created_at"),
                nullableDateTime(rs, "updated_at")
        );
    }

    private List<KeywordSummaryRow> buildKeywordRows(Long projectId,
                                                     LocalDate batchDate,
                                                     String questionTier,
                                                     List<PollSourceRow> sourceRows) {
        Map<String, KeywordAccumulator> accumulators = new LinkedHashMap<>();
        for (PollSourceRow row : sourceRows) {
            KeywordIdentity identity = keywordIdentity(row);
            String dimHash = sha256Hex(canonical(projectId, batchDate, questionTier, identity.value()));
            KeywordAccumulator accumulator = accumulators.computeIfAbsent(dimHash,
                    ignored -> new KeywordAccumulator(projectId, batchDate, questionTier, identity, dimHash));
            accumulator.accept(row);
        }
        return accumulators.values().stream().map(KeywordAccumulator::toRow).toList();
    }

    private List<PlatformSummaryRow> buildPlatformRows(Long projectId,
                                                       LocalDate batchDate,
                                                       String questionTier,
                                                       List<PollSourceRow> sourceRows) {
        Map<String, PlatformAccumulator> accumulators = new LinkedHashMap<>();
        for (PollSourceRow row : sourceRows) {
            if (row.platformId() == null) {
                continue;
            }
            String dimHash = sha256Hex(canonical(projectId, batchDate, questionTier, row.platformId()));
            PlatformAccumulator accumulator = accumulators.computeIfAbsent(dimHash,
                    ignored -> new PlatformAccumulator(projectId, batchDate, questionTier, row.platformId(), dimHash));
            accumulator.accept(row);
        }
        return accumulators.values().stream().map(PlatformAccumulator::toRow).toList();
    }

    private int upsertKeywordRows(List<KeywordSummaryRow> rows) {
        int affected = 0;
        for (KeywordSummaryRow row : rows) {
            affected += jdbcTemplate.update("""
                    INSERT INTO poll_keyword_daily_summary (
                      project_id, batch_date, question_tier, keyword_identity_type, keyword_identity_value, dim_hash,
                      keyword_result_id, keyword_text_snapshot, keyword_text_normalized,
                      source_row_count, platform_count, completed_count, failed_count, hit_count, effective_hit_count,
                      site_mention_count, contact_mention_count, contact_mention_total,
                      search_confirmed_count, brand_search_count, brand_answer_count,
                      confirmed_citation_exposure_count, confirmed_citation_exposure_rate,
                      request_count_total, response_time_ms_total, last_source_created_at, last_source_updated_at,
                      source_checksum, recomputed_at
                    ) VALUES (
                      ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
                    )
                    ON DUPLICATE KEY UPDATE
                      keyword_identity_type = VALUES(keyword_identity_type),
                      keyword_identity_value = VALUES(keyword_identity_value),
                      keyword_result_id = VALUES(keyword_result_id),
                      keyword_text_snapshot = VALUES(keyword_text_snapshot),
                      keyword_text_normalized = VALUES(keyword_text_normalized),
                      source_row_count = VALUES(source_row_count),
                      platform_count = VALUES(platform_count),
                      completed_count = VALUES(completed_count),
                      failed_count = VALUES(failed_count),
                      hit_count = VALUES(hit_count),
                      effective_hit_count = VALUES(effective_hit_count),
                      site_mention_count = VALUES(site_mention_count),
                      contact_mention_count = VALUES(contact_mention_count),
                      contact_mention_total = VALUES(contact_mention_total),
                      search_confirmed_count = VALUES(search_confirmed_count),
                      brand_search_count = VALUES(brand_search_count),
                      brand_answer_count = VALUES(brand_answer_count),
                      confirmed_citation_exposure_count = VALUES(confirmed_citation_exposure_count),
                      confirmed_citation_exposure_rate = VALUES(confirmed_citation_exposure_rate),
                      request_count_total = VALUES(request_count_total),
                      response_time_ms_total = VALUES(response_time_ms_total),
                      last_source_created_at = VALUES(last_source_created_at),
                      last_source_updated_at = VALUES(last_source_updated_at),
                      source_checksum = VALUES(source_checksum),
                      recomputed_at = CURRENT_TIMESTAMP
                    """,
                    row.projectId(), Date.valueOf(row.batchDate()), row.questionTier(), row.keywordIdentityType(),
                    row.keywordIdentityValue(), row.dimHash(), row.keywordResultId(), row.keywordTextSnapshot(),
                    row.keywordTextNormalized(), row.sourceRowCount(), row.platformCount(), row.completedCount(),
                    row.failedCount(), row.hitCount(), row.effectiveHitCount(), row.siteMentionCount(),
                    row.contactMentionCount(), row.contactMentionTotal(), row.searchConfirmedCount(),
                    row.brandSearchCount(), row.brandAnswerCount(), row.confirmedCitationExposureCount(),
                    row.confirmedCitationExposureRate(), row.requestCountTotal(), row.responseTimeMsTotal(),
                    timestamp(row.lastSourceCreatedAt()),
                    timestamp(row.lastSourceUpdatedAt()), row.sourceChecksum());
        }
        return affected;
    }

    private int upsertPlatformRows(List<PlatformSummaryRow> rows) {
        int affected = 0;
        for (PlatformSummaryRow row : rows) {
            affected += jdbcTemplate.update("""
                    INSERT INTO poll_platform_daily_summary (
                      project_id, batch_date, question_tier, platform_id, dim_hash, platform_code, channel_code,
                      platform_name_snapshot,
                      source_row_count, completed_count, failed_count, hit_count, effective_hit_count,
                      site_mention_count, contact_mention_count, contact_mention_total,
                      search_confirmed_count, brand_search_count, brand_answer_count,
                      confirmed_citation_exposure_count, confirmed_citation_exposure_rate,
                      request_count_total, response_time_ms_total, last_source_created_at, last_source_updated_at,
                      source_checksum, recomputed_at
                    ) VALUES (
                      ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
                    )
                    ON DUPLICATE KEY UPDATE
                      platform_code = VALUES(platform_code),
                      channel_code = VALUES(channel_code),
                      platform_name_snapshot = VALUES(platform_name_snapshot),
                      source_row_count = VALUES(source_row_count),
                      completed_count = VALUES(completed_count),
                      failed_count = VALUES(failed_count),
                      hit_count = VALUES(hit_count),
                      effective_hit_count = VALUES(effective_hit_count),
                      site_mention_count = VALUES(site_mention_count),
                      contact_mention_count = VALUES(contact_mention_count),
                      contact_mention_total = VALUES(contact_mention_total),
                      search_confirmed_count = VALUES(search_confirmed_count),
                      brand_search_count = VALUES(brand_search_count),
                      brand_answer_count = VALUES(brand_answer_count),
                      confirmed_citation_exposure_count = VALUES(confirmed_citation_exposure_count),
                      confirmed_citation_exposure_rate = VALUES(confirmed_citation_exposure_rate),
                      request_count_total = VALUES(request_count_total),
                      response_time_ms_total = VALUES(response_time_ms_total),
                      last_source_created_at = VALUES(last_source_created_at),
                      last_source_updated_at = VALUES(last_source_updated_at),
                      source_checksum = VALUES(source_checksum),
                      recomputed_at = CURRENT_TIMESTAMP
                    """,
                    row.projectId(), Date.valueOf(row.batchDate()), row.questionTier(), row.platformId(),
                    row.dimHash(), row.platformCode(), row.channelCode(), row.platformNameSnapshot(), row.sourceRowCount(),
                    row.completedCount(), row.failedCount(), row.hitCount(), row.effectiveHitCount(),
                    row.siteMentionCount(), row.contactMentionCount(), row.contactMentionTotal(),
                    row.searchConfirmedCount(), row.brandSearchCount(), row.brandAnswerCount(),
                    row.confirmedCitationExposureCount(), row.confirmedCitationExposureRate(),
                    row.requestCountTotal(), row.responseTimeMsTotal(), timestamp(row.lastSourceCreatedAt()),
                    timestamp(row.lastSourceUpdatedAt()), row.sourceChecksum());
        }
        return affected;
    }

    private int deleteKeywordZombies(Long projectId, LocalDate batchDate, String questionTier, List<KeywordSummaryRow> presentRows) {
        return deleteZombies("poll_keyword_daily_summary", projectId, batchDate, questionTier,
                presentRows.stream().map(KeywordSummaryRow::dimHash).toList());
    }

    private int deletePlatformZombies(Long projectId, LocalDate batchDate, String questionTier, List<PlatformSummaryRow> presentRows) {
        return deleteZombies("poll_platform_daily_summary", projectId, batchDate, questionTier,
                presentRows.stream().map(PlatformSummaryRow::dimHash).toList());
    }

    private int deleteZombies(String table, Long projectId, LocalDate batchDate, String questionTier, List<String> presentDimHashes) {
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        args.add(Date.valueOf(batchDate));
        args.add(questionTier);
        if (presentDimHashes.isEmpty()) {
            return jdbcTemplate.update("""
                    DELETE FROM %s
                     WHERE project_id = ?
                       AND batch_date = ?
                       AND question_tier = ?
                    """.formatted(table), args.toArray());
        }

        String placeholders = String.join(", ", presentDimHashes.stream().map(ignored -> "?").toList());
        args.addAll(presentDimHashes);
        return jdbcTemplate.update("""
                DELETE FROM %s
                 WHERE project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                   AND dim_hash NOT IN (%s)
                """.formatted(table, placeholders), args.toArray());
    }

    private KeywordIdentity keywordIdentity(PollSourceRow row) {
        if (row.keywordResultId() != null) {
            return new KeywordIdentity("ID", "ID:" + row.keywordResultId(), row.keywordResultId(), normalizeKeywordText(row.keywordTextSnapshot()));
        }
        String normalizedText = normalizeKeywordText(row.keywordTextSnapshot());
        return new KeywordIdentity("TEXT", "TEXT:" + normalizedText, null, normalizedText);
    }

    private static String normalizeKeywordText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeTier(String questionTier) {
        return StringUtils.hasText(questionTier) ? questionTier.trim().toUpperCase(Locale.ROOT) : "A";
    }

    private static String canonical(Object... fields) {
        StringJoiner joiner = new StringJoiner(FIELD_SEPARATOR);
        for (Object field : fields) {
            if (field == null) {
                joiner.add(NULL_SENTINEL);
            } else {
                String value = String.valueOf(field).trim();
                joiner.add(value.isEmpty() ? "" : value);
            }
        }
        return joiner.toString();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
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

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    public record RecomputeResult(Long projectId,
                                  LocalDate batchDate,
                                  String questionTier,
                                  boolean skipped,
                                  String skipReason,
                                  int sourceRowCount,
                                  int keywordSummaryCount,
                                  int platformSummaryCount,
                                  int keywordUpsertAffected,
                                  int platformUpsertAffected,
                                  int keywordZombieDeleted,
                                  int platformZombieDeleted) {
        static RecomputeResult skipped(Long projectId, LocalDate batchDate, String questionTier, String reason) {
            return new RecomputeResult(projectId, batchDate, questionTier, true, reason, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record PollSourceRow(Long id,
                                 Long projectId,
                                 Long keywordResultId,
                                 String keywordTextSnapshot,
                                 String questionTier,
                                 Long platformId,
                                 String platformCode,
                                 String channelCode,
                                 String platformNameSnapshot,
                                 String status,
                                 Integer requestCount,
                                 Long responseTimeMs,
                                 Boolean isHit,
                                 Boolean effectiveHit,
                                 Boolean siteMentioned,
                                 Boolean contactMentioned,
                                 Integer contactMentionCount,
                                 Long effectiveAttemptId,
                                 Boolean executionFinalized,
                                 Boolean searchRequested,
                                 Boolean searchTriggered,
                                 Boolean brandInSearch,
                                 Boolean brandInAnswer,
                                 Boolean confirmedCitationExposure,
                                 String recordType,
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt) {
    }

    private record KeywordIdentity(String type, String value, Long keywordResultId, String normalizedText) {
    }

    private static final class KeywordAccumulator extends BaseAccumulator {
        private final Long projectId;
        private final LocalDate batchDate;
        private final String questionTier;
        private final KeywordIdentity identity;
        private final String dimHash;
        private final Map<Long, Boolean> platformIds = new HashMap<>();
        private String keywordTextSnapshot;
        private LocalDateTime latestTextAt;
        private Long latestTextRowId;

        private KeywordAccumulator(Long projectId, LocalDate batchDate, String questionTier, KeywordIdentity identity, String dimHash) {
            this.projectId = projectId;
            this.batchDate = batchDate;
            this.questionTier = questionTier;
            this.identity = identity;
            this.dimHash = dimHash;
        }

        @Override
        void accept(PollSourceRow row) {
            super.accept(row);
            if (row.platformId() != null) {
                platformIds.put(row.platformId(), Boolean.TRUE);
            }
            if (isLater(row, latestTextAt, latestTextRowId)) {
                keywordTextSnapshot = row.keywordTextSnapshot();
                latestTextAt = latestAt(row);
                latestTextRowId = row.id();
            }
        }

        KeywordSummaryRow toRow() {
            return new KeywordSummaryRow(projectId, batchDate, questionTier, identity.type(), identity.value(), dimHash,
                    identity.keywordResultId(), keywordTextSnapshot, identity.normalizedText(), sourceRows.size(),
                    platformIds.size(), completedCount, failedCount, hitCount, effectiveHitCount, siteMentionCount,
                    contactMentionCount, contactMentionTotal, searchConfirmedCount, brandSearchCount, brandAnswerCount,
                    confirmedCitationExposureCount, confirmedCitationExposureRate(), requestCountTotal, responseTimeMsTotal,
                    lastSourceCreatedAt, lastSourceUpdatedAt, sourceChecksum());
        }
    }

    private static final class PlatformAccumulator extends BaseAccumulator {
        private final Long projectId;
        private final LocalDate batchDate;
        private final String questionTier;
        private final Long platformId;
        private final String dimHash;
        private String platformCode;
        private String channelCode;
        private String platformNameSnapshot;
        private LocalDateTime latestPlatformAt;
        private Long latestPlatformRowId;

        private PlatformAccumulator(Long projectId, LocalDate batchDate, String questionTier, Long platformId, String dimHash) {
            this.projectId = projectId;
            this.batchDate = batchDate;
            this.questionTier = questionTier;
            this.platformId = platformId;
            this.dimHash = dimHash;
        }

        @Override
        void accept(PollSourceRow row) {
            super.accept(row);
            if (isLater(row, latestPlatformAt, latestPlatformRowId)) {
                platformCode = row.platformCode();
                channelCode = row.channelCode();
                platformNameSnapshot = row.platformNameSnapshot();
                latestPlatformAt = latestAt(row);
                latestPlatformRowId = row.id();
            }
        }

        PlatformSummaryRow toRow() {
            return new PlatformSummaryRow(projectId, batchDate, questionTier, platformId, dimHash,
                    platformCode == null ? "" : platformCode,
                    channelCode == null || channelCode.isBlank() ? platformCode : channelCode,
                    platformNameSnapshot, sourceRows.size(), completedCount,
                    failedCount, hitCount, effectiveHitCount, siteMentionCount, contactMentionCount,
                    contactMentionTotal, searchConfirmedCount, brandSearchCount, brandAnswerCount,
                    confirmedCitationExposureCount, confirmedCitationExposureRate(), requestCountTotal,
                    responseTimeMsTotal, lastSourceCreatedAt,
                    lastSourceUpdatedAt, sourceChecksum());
        }
    }

    private abstract static class BaseAccumulator {
        final List<PollSourceRow> sourceRows = new ArrayList<>();
        int completedCount;
        int failedCount;
        int hitCount;
        int effectiveHitCount;
        int siteMentionCount;
        int contactMentionCount;
        int searchConfirmedCount;
        int brandSearchCount;
        int brandAnswerCount;
        int confirmedCitationExposureCount;
        long contactMentionTotal;
        long requestCountTotal;
        long responseTimeMsTotal;
        LocalDateTime lastSourceCreatedAt;
        LocalDateTime lastSourceUpdatedAt;

        void accept(PollSourceRow row) {
            sourceRows.add(row);
            if ("completed".equalsIgnoreCase(row.status())) {
                completedCount++;
            }
            if ("failed".equalsIgnoreCase(row.status()) || "error".equalsIgnoreCase(row.recordType())) {
                failedCount++;
            }
            if (Boolean.TRUE.equals(row.isHit())) {
                hitCount++;
            }
            if (Boolean.TRUE.equals(row.effectiveHit())) {
                effectiveHitCount++;
            }
            if (Boolean.TRUE.equals(row.siteMentioned())) {
                siteMentionCount++;
            }
            if (Boolean.TRUE.equals(row.contactMentioned())) {
                contactMentionCount++;
            }
            if (isEffectiveWebSearchResult(row)) {
                if (Boolean.TRUE.equals(row.searchTriggered())) {
                    searchConfirmedCount++;
                    if (Boolean.TRUE.equals(row.brandInSearch())) {
                        brandSearchCount++;
                    }
                    if (Boolean.TRUE.equals(row.brandInAnswer())) {
                        brandAnswerCount++;
                    }
                    if (Boolean.TRUE.equals(row.confirmedCitationExposure())) {
                        confirmedCitationExposureCount++;
                    }
                }
            }
            contactMentionTotal += row.contactMentionCount() == null ? 0 : row.contactMentionCount();
            requestCountTotal += row.requestCount() == null ? 0 : row.requestCount();
            responseTimeMsTotal += row.responseTimeMs() == null ? 0L : row.responseTimeMs();
            lastSourceCreatedAt = max(lastSourceCreatedAt, row.createdAt());
            lastSourceUpdatedAt = max(lastSourceUpdatedAt, row.updatedAt());
        }

        String sourceChecksum() {
            return sha256Hex(sourceRows.stream()
                    .sorted(Comparator.comparing(PollSourceRow::id))
                    .map(row -> canonical(row.id(), row.keywordResultId(), row.keywordTextSnapshot(), row.platformId(),
                            row.platformCode(), row.status(), row.requestCount(), row.responseTimeMs(), row.isHit(),
                            row.effectiveHit(), row.siteMentioned(), row.contactMentioned(), row.contactMentionCount(),
                            row.channelCode(), row.effectiveAttemptId(), row.executionFinalized(), row.searchRequested(),
                            row.searchTriggered(), row.brandInSearch(), row.brandInAnswer(),
                            row.confirmedCitationExposure(),
                            row.recordType(), row.createdAt(), row.updatedAt()))
                    .collect(Collectors.joining(RECORD_SEPARATOR)));
        }

        BigDecimal confirmedCitationExposureRate() {
            if (completedCount <= 0) {
                return BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
            }
            return BigDecimal.valueOf(confirmedCitationExposureCount)
                    .divide(BigDecimal.valueOf(completedCount), 4, RoundingMode.HALF_UP);
        }

        private boolean isEffectiveWebSearchResult(PollSourceRow row) {
            return Boolean.TRUE.equals(row.executionFinalized())
                    && row.effectiveAttemptId() != null
                    && Boolean.TRUE.equals(row.searchRequested());
        }
    }

    private static boolean isLater(PollSourceRow row, LocalDateTime currentAt, Long currentRowId) {
        LocalDateTime candidateAt = latestAt(row);
        if (currentAt == null) {
            return true;
        }
        int compared = candidateAt.compareTo(currentAt);
        if (compared != 0) {
            return compared > 0;
        }
        return currentRowId == null || row.id() > currentRowId;
    }

    private static LocalDateTime latestAt(PollSourceRow row) {
        return Objects.requireNonNullElseGet(row.updatedAt(), () -> Objects.requireNonNullElse(row.createdAt(), LocalDateTime.MIN));
    }

    private static LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return right.isAfter(left) ? right : left;
    }

    private record KeywordSummaryRow(Long projectId,
                                     LocalDate batchDate,
                                     String questionTier,
                                     String keywordIdentityType,
                                     String keywordIdentityValue,
                                     String dimHash,
                                     Long keywordResultId,
                                     String keywordTextSnapshot,
                                     String keywordTextNormalized,
                                     int sourceRowCount,
                                     int platformCount,
                                     int completedCount,
                                     int failedCount,
                                     int hitCount,
                                     int effectiveHitCount,
                                     int siteMentionCount,
                                     int contactMentionCount,
                                     long contactMentionTotal,
                                     int searchConfirmedCount,
                                     int brandSearchCount,
                                     int brandAnswerCount,
                                     int confirmedCitationExposureCount,
                                     BigDecimal confirmedCitationExposureRate,
                                     long requestCountTotal,
                                     long responseTimeMsTotal,
                                     LocalDateTime lastSourceCreatedAt,
                                     LocalDateTime lastSourceUpdatedAt,
                                     String sourceChecksum) {
    }

    private record PlatformSummaryRow(Long projectId,
                                      LocalDate batchDate,
                                      String questionTier,
                                      Long platformId,
                                      String dimHash,
                                      String platformCode,
                                      String channelCode,
                                      String platformNameSnapshot,
                                      int sourceRowCount,
                                      int completedCount,
                                      int failedCount,
                                      int hitCount,
                                      int effectiveHitCount,
                                      int siteMentionCount,
                                      int contactMentionCount,
                                      long contactMentionTotal,
                                      int searchConfirmedCount,
                                      int brandSearchCount,
                                      int brandAnswerCount,
                                      int confirmedCitationExposureCount,
                                      BigDecimal confirmedCitationExposureRate,
                                      long requestCountTotal,
                                      long responseTimeMsTotal,
                                      LocalDateTime lastSourceCreatedAt,
                                      LocalDateTime lastSourceUpdatedAt,
                                      String sourceChecksum) {
    }
}
