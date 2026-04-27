package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PresaleReusePersistenceService {

    private final PresaleAiCallMapper aiCallMapper;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final ReuseDecisionService reuseDecisionService;

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void cleanupLegacySingleCompetitorBatch2Rows(Long versionId, String competitorGroupName) {
        if (versionId == null || competitorGroupName == null
                || !competitorGroupName.contains(CompetitorGroupKeyUtils.SEPARATOR)) {
            return;
        }
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
        deletePromptResultByKey(ctx);
        deleteFailedAnalyzeCallsByKey(ctx);

        newAnalyzeCall.setParentCallId(reusedQueryCall.getId());
        aiCallMapper.insert(newAnalyzeCall);

        newPromptResult.setQueryCallId(reusedQueryCall.getId());
        newPromptResult.setAnalyzeCallId(newAnalyzeCall.getId());
        aiPromptResultMapper.insert(newPromptResult);
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
