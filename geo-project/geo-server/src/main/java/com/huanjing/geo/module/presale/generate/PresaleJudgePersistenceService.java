package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptJudgeResult;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptJudgeResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Atomically fences judge persistence against a newer generation attempt. */
@Service
@RequiredArgsConstructor
public class PresaleJudgePersistenceService {

    private final PresaleReportVersionMapper versionMapper;
    private final PresaleAiPromptJudgeResultMapper judgeResultMapper;

    @Transactional(rollbackFor = Exception.class)
    public void persistSuccess(Long versionId,
                               long generationAttempt,
                               Long promptResultId,
                               String legacyCompetitorGroup,
                               List<PresaleAiPromptJudgeResult> rows) {
        requireCurrentRun(versionId, generationAttempt);
        if (legacyCompetitorGroup != null && promptResultId != null) {
            judgeResultMapper.delete(new LambdaQueryWrapper<PresaleAiPromptJudgeResult>()
                    .eq(PresaleAiPromptJudgeResult::getPromptResultId, promptResultId)
                    .eq(PresaleAiPromptJudgeResult::getCompetitorName, legacyCompetitorGroup));
        }
        if (rows != null) {
            rows.forEach(judgeResultMapper::upsertByPromptResultId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistFailure(PresaleAiPromptJudgeResult row, long generationAttempt) {
        requireCurrentRun(row == null ? null : row.getVersionId(), generationAttempt);
        judgeResultMapper.upsertByPromptResultId(row);
    }

    private void requireCurrentRun(Long versionId, long generationAttempt) {
        if (generationAttempt <= 0L) {
            return;
        }
        Long currentAttempt = versionMapper.selectRunningAttemptForUpdate(versionId);
        if (currentAttempt == null || currentAttempt != generationAttempt) {
            throw new BatchInterruptedException("generation attempt superseded during judge persistence");
        }
    }
}
