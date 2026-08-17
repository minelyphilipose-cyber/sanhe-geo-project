package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.function.Supplier;

@Service
public class PollRetentionSliceWriteService {

    private final PollRetentionSliceGuardService sliceGuardService;
    private final JdbcTemplate jdbc;

    @Autowired
    public PollRetentionSliceWriteService(PollRetentionSliceGuardService sliceGuardService, JdbcTemplate jdbc) {
        this.sliceGuardService = sliceGuardService;
        this.jdbc = jdbc;
    }

    public PollRetentionSliceWriteService(PollRetentionSliceGuardService sliceGuardService) {
        this(sliceGuardService, null);
    }

    @Transactional
    public void execute(Long projectId,
                        LocalDate batchDate,
                        String questionTier,
                        Runnable operation) {
        execute(projectId, batchDate, questionTier, () -> {
            operation.run();
            return null;
        });
    }

    @Transactional
    public <T> T execute(Long projectId,
                         LocalDate batchDate,
                         String questionTier,
                         Supplier<T> operation) {
        sliceGuardService.lockAndRequireWritable(projectId, batchDate, questionTier);
        return operation.get();
    }

    @Transactional
    public <T> T executeByAttemptId(Long attemptId, Supplier<T> operation) {
        SliceIdentity identity = attemptIdentity(attemptId, false);
        sliceGuardService.lockAndRequireWritable(identity.projectId(), identity.batchDate(), identity.questionTier());
        SliceIdentity locked = attemptIdentity(attemptId, true);
        if (!identity.equals(locked)) {
            throw new BizException(409, "Poll attempt slice identity changed while acquiring retention fence");
        }
        return operation.get();
    }

    @Transactional
    public <T> T executeByShardId(Long shardId, Supplier<T> operation) {
        SliceIdentity identity = shardIdentity(shardId, false);
        sliceGuardService.lockAndRequireWritable(identity.projectId(), identity.batchDate(), identity.questionTier());
        SliceIdentity locked = shardIdentity(shardId, true);
        if (!identity.equals(locked)) {
            throw new BizException(409, "Poll shard slice identity changed while acquiring retention fence");
        }
        return operation.get();
    }

    @Transactional
    public <T> T executeByProviderCallId(Long callId, Supplier<T> operation) {
        SliceIdentity identity = providerCallIdentity(callId, false);
        sliceGuardService.lockAndRequireWritable(identity.projectId(), identity.batchDate(), identity.questionTier());
        SliceIdentity locked = providerCallIdentity(callId, true);
        if (!identity.equals(locked)) {
            throw new BizException(409, "Provider call parent changed while acquiring retention fence");
        }
        return operation.get();
    }

    @Transactional
    public <T> T executeByBatchId(Long batchId, Supplier<T> operation) {
        SliceIdentity identity = batchIdentity(batchId, false);
        sliceGuardService.lockAndRequireWritable(identity.projectId(), identity.batchDate(), identity.questionTier());
        SliceIdentity locked = batchIdentity(batchId, true);
        if (!identity.equals(locked)) {
            throw new BizException(409, "Poll batch slice identity changed while acquiring retention fence");
        }
        return operation.get();
    }

    private SliceIdentity attemptIdentity(Long attemptId, boolean lock) {
        if (attemptId == null || attemptId <= 0) throw new IllegalArgumentException("attemptId is required");
        var rows = jdbc.query("""
                SELECT a.poll_result_id, r.project_id, r.batch_date, r.question_tier
                  FROM poll_invocation_attempts a
                  JOIN poll_results r ON r.id = a.poll_result_id
                 WHERE a.id = ?%s
                """.formatted(lock ? " FOR UPDATE" : ""),
                (rs, rowNum) -> new SliceIdentity(rs.getLong("project_id"),
                        rs.getDate("batch_date").toLocalDate(), rs.getString("question_tier"),
                        rs.getLong("poll_result_id"), rs.getLong("poll_result_id")), attemptId);
        if (rows.size() != 1) throw new BizException(404, "Invocation attempt not found: " + attemptId);
        return rows.get(0);
    }

    private SliceIdentity shardIdentity(Long shardId, boolean lock) {
        if (shardId == null || shardId <= 0) throw new IllegalArgumentException("shardId is required");
        var rows = jdbc.query("""
                SELECT s.batch_id, b.project_id, b.batch_date, b.question_tier
                  FROM poll_batch_shards s
                  JOIN poll_batches b ON b.id = s.batch_id
                 WHERE s.id = ?%s
                """.formatted(lock ? " FOR UPDATE" : ""),
                (rs, rowNum) -> new SliceIdentity(rs.getLong("project_id"),
                        rs.getDate("batch_date").toLocalDate(), rs.getString("question_tier"),
                        rs.getLong("batch_id"), rs.getLong("batch_id")), shardId);
        if (rows.size() != 1) throw new BizException(404, "Poll shard not found: " + shardId);
        return rows.get(0);
    }

    private SliceIdentity providerCallIdentity(Long callId, boolean lock) {
        if (callId == null || callId <= 0) throw new IllegalArgumentException("callId is required");
        var rows = jdbc.query("""
                SELECT c.attempt_id, a.poll_result_id, r.project_id, r.batch_date, r.question_tier
                  FROM poll_provider_calls c
                  JOIN poll_invocation_attempts a ON a.id = c.attempt_id
                  JOIN poll_results r ON r.id = a.poll_result_id
                 WHERE c.id = ?%s
                """.formatted(lock ? " FOR UPDATE" : ""),
                (rs, rowNum) -> new SliceIdentity(rs.getLong("project_id"),
                        rs.getDate("batch_date").toLocalDate(), rs.getString("question_tier"),
                        rs.getLong("attempt_id"), rs.getLong("poll_result_id")), callId);
        if (rows.size() != 1) throw new BizException(404, "Provider call not found: " + callId);
        return rows.get(0);
    }

    private SliceIdentity batchIdentity(Long batchId, boolean lock) {
        if (batchId == null || batchId <= 0) throw new IllegalArgumentException("batchId is required");
        var rows = jdbc.query("""
                SELECT project_id, batch_date, question_tier
                  FROM poll_batches WHERE id = ?%s
                """.formatted(lock ? " FOR UPDATE" : ""),
                (rs, rowNum) -> new SliceIdentity(rs.getLong("project_id"),
                        rs.getDate("batch_date").toLocalDate(), rs.getString("question_tier"), batchId, batchId),
                batchId);
        if (rows.size() != 1) throw new BizException(404, "Poll batch not found: " + batchId);
        return rows.get(0);
    }

    private record SliceIdentity(Long projectId, LocalDate batchDate, String questionTier,
                                 Long parentId, Long rootResultId) { }
}
