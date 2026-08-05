package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PresaleReusePersistenceService {

    private final PresaleAiCallMapper aiCallMapper;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final PresaleReportVersionMapper versionMapper;
    private final ReuseDecisionService reuseDecisionService;

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void cleanupLegacySingleCompetitorBatch2Rows(Long versionId, String competitorGroupName) {
        cleanupLegacySingleCompetitorBatch2Rows(versionId, 0L, competitorGroupName);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void cleanupLegacySingleCompetitorBatch2Rows(Long versionId,
                                                        long generationAttempt,
                                                        String competitorGroupName) {
        if (versionId == null || competitorGroupName == null
                || !competitorGroupName.contains(CompetitorGroupKeyUtils.SEPARATOR)) {
            return;
        }
        requireCurrentRun(versionId, generationAttempt,
                "generation attempt superseded before legacy batch2 cleanup");
        aiPromptResultMapper.delete(new LambdaQueryWrapper<PresaleAiPromptResult>()
                .eq(PresaleAiPromptResult::getVersionId, versionId)
                .eq(PresaleAiPromptResult::getBatchNo, 2)
                .notLike(PresaleAiPromptResult::getCompetitorName, CompetitorGroupKeyUtils.SEPARATOR));
        aiCallMapper.delete(new LambdaQueryWrapper<PresaleAiCall>()
                .eq(PresaleAiCall::getVersionId, versionId)
                .eq(PresaleAiCall::getBatchNo, 2)
                .notLike(PresaleAiCall::getCompetitorName, CompetitorGroupKeyUtils.SEPARATOR));
    }

    /**
     * ⚠ 事务回滚已知盲区:
     * 当前仅 Mockito 单测覆盖逻辑分支(PresaleReusePersistenceServiceTest
     * .retry_replaceAnalyze_transactionRollback_noNewAnalyzeCallResidue_logicBranch),
     * Spring @Transactional 真回滚未在自动化测试覆盖,
     * 依赖代码审查(事务注解位置 + 传播级别 + 方法可见性)兜底。
     * 后续建集成测试基础设施时补齐 @DataMybatisTest / @SpringBootTest 切片测试。
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void replaceFailedAnalyzeAndResult(PlatformCallContext ctx,
                                              PresaleAiCall reusedQueryCall,
                                              PresaleAiCall newAnalyzeCall,
                                              PresaleAiPromptResult newPromptResult) {
        requireCurrentRun(ctx);
        deletePromptResultByKey(ctx);
        deleteFailedAnalyzeCallsByKey(ctx);

        newAnalyzeCall.setParentCallId(reusedQueryCall.getId());
        int callInserted = ctx.generationAttempt() > 0L
                ? aiCallMapper.insertForCurrentRun(newAnalyzeCall, ctx.generationAttempt())
                : aiCallMapper.insert(newAnalyzeCall);
        if (callInserted == 0 && ctx.generationAttempt() > 0L) {
            throw new BatchInterruptedException("generation attempt superseded during reused ANALYZE insert");
        }

        newPromptResult.setQueryCallId(reusedQueryCall.getId());
        newPromptResult.setAnalyzeCallId(newAnalyzeCall.getId());
        int resultInserted = ctx.generationAttempt() > 0L
                ? aiPromptResultMapper.upsertForCurrentRun(newPromptResult, ctx.generationAttempt())
                : aiPromptResultMapper.insert(newPromptResult);
        if (resultInserted == 0 && ctx.generationAttempt() > 0L) {
            throw new BatchInterruptedException("generation attempt superseded during reused result upsert");
        }
    }

    private void requireCurrentRun(PlatformCallContext ctx) {
        if (ctx == null || ctx.generationAttempt() <= 0L) {
            return;
        }
        requireCurrentRun(ctx.versionId(), ctx.generationAttempt(),
                "generation attempt superseded before reused ANALYZE persistence");
    }

    private void requireCurrentRun(Long versionId, long generationAttempt, String message) {
        if (generationAttempt <= 0L) {
            return;
        }
        Long currentAttempt = versionMapper == null
                ? null
                : versionMapper.selectRunningAttemptForUpdate(versionId);
        if (currentAttempt == null || currentAttempt.longValue() != generationAttempt) {
            throw new BatchInterruptedException(message);
        }
    }

    private void deletePromptResultByKey(PlatformCallContext ctx) {
        LambdaQueryWrapper<PresaleAiPromptResult> wrapper = new LambdaQueryWrapper<PresaleAiPromptResult>()
                .eq(PresaleAiPromptResult::getVersionId, ctx.versionId())
                .eq(PresaleAiPromptResult::getBatchNo, ctx.batchNo())
                .eq(PresaleAiPromptResult::getPlatformCode, ctx.platformCode())
                .eq(PresaleAiPromptResult::getPromptTemplateId, ctx.promptTemplateId());
        applyCompetitorFilterPromptResult(wrapper, ctx.competitorName());
        aiPromptResultMapper.delete(wrapper);
    }

    private void deleteFailedAnalyzeCallsByKey(PlatformCallContext ctx) {
        LambdaQueryWrapper<PresaleAiCall> wrapper = new LambdaQueryWrapper<PresaleAiCall>()
                .eq(PresaleAiCall::getVersionId, ctx.versionId())
                .eq(PresaleAiCall::getBatchNo, ctx.batchNo())
                .eq(PresaleAiCall::getPlatformCode, ctx.platformCode())
                .eq(PresaleAiCall::getPromptTemplateId, ctx.promptTemplateId())
                .eq(PresaleAiCall::getStage, "ANALYZE")
                .eq(PresaleAiCall::getCallStatus, "FAILED");
        applyCompetitorFilterAiCall(wrapper, ctx.competitorName());
        aiCallMapper.delete(wrapper);
    }

    private void applyCompetitorFilterPromptResult(LambdaQueryWrapper<PresaleAiPromptResult> wrapper,
                                                   String competitorName) {
        String normalized = reuseDecisionService.normalizeCompetitor(competitorName);
        if (normalized.isEmpty()) {
            wrapper.and(w -> w.eq(PresaleAiPromptResult::getCompetitorName, "")
                    .or()
                    .isNull(PresaleAiPromptResult::getCompetitorName));
            return;
        }
        wrapper.eq(PresaleAiPromptResult::getCompetitorName, normalized);
    }

    private void applyCompetitorFilterAiCall(LambdaQueryWrapper<PresaleAiCall> wrapper,
                                             String competitorName) {
        String normalized = reuseDecisionService.normalizeCompetitor(competitorName);
        if (normalized.isEmpty()) {
            wrapper.and(w -> w.eq(PresaleAiCall::getCompetitorName, "")
                    .or()
                    .isNull(PresaleAiCall::getCompetitorName));
            return;
        }
        wrapper.eq(PresaleAiCall::getCompetitorName, normalized);
    }
}
