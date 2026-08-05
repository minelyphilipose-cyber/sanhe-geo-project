package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.dispatch.entity.PollBatchShardItem;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardItemMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.retention.service.PollRetentionSliceGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PollResultPersistenceTransactionService {

    private static final String TRIGGER_TYPE_SCHEDULED = "SCHEDULED";

    private final PollBatchShardItemMapper pollBatchShardItemMapper;
    private final PollResultMapper pollResultMapper;
    private final PollRetentionSliceGuardService retentionSliceGuardService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollResult ensurePollResult(PollResult result) {
        normalizeRequiredIdentity(result);
        retentionSliceGuardService.lockAndRequireWritable(result);
        PollResult existing = findPollResult(result);
        if (existing != null) {
            return existing;
        }
        pollResultMapper.insert(result);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollResult upsertPollResultAndMarkItem(PollResult result, PollBatchShardItem item) {
        normalizeRequiredIdentity(result);
        retentionSliceGuardService.lockAndRequireWritable(result);
        PollResult existing = findPollResult(result);
        if (existing == null) {
            pollResultMapper.insert(result);
        } else {
            result.setId(existing.getId());
            pollResultMapper.updateById(result);
        }

        String itemStatus = "completed".equals(result.getStatus()) ? "completed" : "failed";
        String lastError = "completed".equals(result.getStatus()) ? null : trim(result.getDetailJson());
        int updated = pollBatchShardItemMapper.markResultProjected(
                item.getId(), result.getId(), itemStatus, lastError);
        if (updated != 1) {
            PollBatchShardItem current = pollBatchShardItemMapper.selectById(item.getId());
            if (current == null || !isTerminal(current.getStatus())) {
                throw new IllegalStateException("Poll shard item changed while projecting result: " + item.getId());
            }
        }
        item.setPollResultId(result.getId());
        item.setStatus(itemStatus);
        item.setLastError(lastError);
        item.setResultSnapshotJson(null);
        item.setResultSnapshotAt(null);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stagePollResult(PollBatchShardItem item, PollResult result, String snapshotJson) {
        Objects.requireNonNull(item, "Poll shard item must not be null");
        normalizeRequiredIdentity(result);
        if (item.getId() == null || !StringUtils.hasText(snapshotJson)) {
            throw new IllegalArgumentException("Poll result snapshot identity and payload are required");
        }
        retentionSliceGuardService.lockAndRequireWritable(result);
        LocalDateTime snapshotAt = LocalDateTime.now();
        int updated = pollBatchShardItemMapper.stageResultSnapshot(item.getId(), snapshotJson, snapshotAt);
        if (updated != 1) {
            PollBatchShardItem current = pollBatchShardItemMapper.selectById(item.getId());
            if (current == null || !isTerminal(current.getStatus())) {
                throw new IllegalStateException("Poll shard item changed while staging result: " + item.getId());
            }
            return;
        }
        item.setResultSnapshotJson(snapshotJson);
        item.setResultSnapshotAt(snapshotAt);
    }

    private PollResult findPollResult(PollResult result) {
        LambdaQueryWrapper<PollResult> wrapper = new LambdaQueryWrapper<PollResult>()
                .eq(PollResult::getProjectId, result.getProjectId())
                .eq(PollResult::getPlatformId, result.getPlatformId())
                .eq(PollResult::getBatchDate, result.getBatchDate())
                .eq(PollResult::getBatchNo, result.getBatchNo())
                .eq(PollResult::getQuestionTier, result.getQuestionTier());
        if (result.getKeywordResultId() == null) {
            wrapper.isNull(PollResult::getKeywordResultId)
                    .eq(PollResult::getKeywordTextSnapshot, result.getKeywordTextSnapshot());
        } else {
            wrapper.eq(PollResult::getKeywordResultId, result.getKeywordResultId());
        }
        return pollResultMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    private void normalizeRequiredIdentity(PollResult result) {
        Objects.requireNonNull(result, "Poll result must not be null");
        if (!StringUtils.hasText(result.getChannelCode())) {
            if (!StringUtils.hasText(result.getPlatformCode())) {
                throw new IllegalArgumentException("Poll result platformCode is required when channelCode is blank");
            }
            result.setChannelCode(result.getPlatformCode().trim());
        } else {
            result.setChannelCode(result.getChannelCode().trim());
        }
        if (!StringUtils.hasText(result.getTriggerType())) {
            result.setTriggerType(TRIGGER_TYPE_SCHEDULED);
        } else {
            result.setTriggerType(result.getTriggerType().trim());
        }
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 900 ? value : value.substring(0, 900);
    }

    private boolean isTerminal(String status) {
        return "completed".equals(status) || "failed".equals(status);
    }
}
