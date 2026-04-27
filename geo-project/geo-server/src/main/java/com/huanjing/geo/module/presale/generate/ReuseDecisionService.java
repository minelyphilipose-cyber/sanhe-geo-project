package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReuseDecisionService {

    private final PresaleAiCallMapper aiCallMapper;

    public Map<ReuseKey, ReuseSnapshot> preloadByVersionAndBatch(Long versionId, int batchNo) {
        List<PresaleAiCall> calls = aiCallMapper.selectList(
                new LambdaQueryWrapper<PresaleAiCall>()
                        .eq(PresaleAiCall::getVersionId, versionId)
                        .eq(PresaleAiCall::getBatchNo, batchNo)
                        .orderByDesc(PresaleAiCall::getId)
        );
        Map<ReuseKey, ReuseSnapshotBuilder> builders = new HashMap<>();
        for (PresaleAiCall call : calls) {
            ReuseKey key = keyOf(
                    call.getVersionId(),
                    call.getBatchNo(),
                    call.getPlatformCode(),
                    call.getPromptTemplateId(),
                    call.getCompetitorName()
            );
            ReuseSnapshotBuilder builder = builders.computeIfAbsent(key, k -> new ReuseSnapshotBuilder());
            if ("ANALYZE".equals(call.getStage()) && "SUCCESS".equals(call.getCallStatus())) {
                builder.hasAnalyzeSuccess = true;
                continue;
            }
            if ("QUERY".equals(call.getStage()) && "SUCCESS".equals(call.getCallStatus()) && builder.querySuccessCall == null) {
                builder.querySuccessCall = call;
            }
        }
        Map<ReuseKey, ReuseSnapshot> snapshots = new HashMap<>();
        builders.forEach((k, v) -> snapshots.put(k, new ReuseSnapshot(v.hasAnalyzeSuccess, v.querySuccessCall)));
        return snapshots;
    }

    public ReuseDecision decide(PlatformCallContext ctx, Map<ReuseKey, ReuseSnapshot> cache) {
        ReuseSnapshot snapshot = cache.get(keyOf(ctx));
        if (snapshot == null) {
            return ReuseDecision.RUN_FULL;
        }
        if (snapshot.hasAnalyzeSuccess()) {
            return ReuseDecision.SKIP_ALL;
        }
        if (snapshot.querySuccessCall() != null) {
            return ReuseDecision.REUSE_QUERY_ONLY;
        }
        return ReuseDecision.RUN_FULL;
    }

    public ReuseSnapshot snapshotOf(PlatformCallContext ctx, Map<ReuseKey, ReuseSnapshot> cache) {
        return cache.get(keyOf(ctx));
    }

    public ReuseKey keyOf(PlatformCallContext ctx) {
        return keyOf(ctx.versionId(), ctx.batchNo(), ctx.platformCode(), ctx.promptTemplateId(), ctx.competitorName());
    }

    public ReuseKey keyOf(Long versionId,
                          Integer batchNo,
                          String platformCode,
                          Long promptTemplateId,
                          String competitorName) {
        return new ReuseKey(
                versionId,
                batchNo,
                platformCode == null ? "" : platformCode.trim().toLowerCase(Locale.ROOT),
                promptTemplateId,
                normalizeCompetitor(competitorName)
        );
    }

    /**
     * 复用键归一化:
     * - 单竞品:保持历史 trim-only 语义。
     * - 竞品组:拆分、trim、排序后拼接,避免 Top 竞品顺序波动导致复用失效。
     *
     * 注意:DB 入库 competitor_name 仍保持用户可见的 Top 排名顺序,复用 key 与入库值不是同一个概念。
     */
    public String normalizeCompetitor(String competitorName) {
        return CompetitorGroupKeyUtils.reuseKey(competitorName);
    }

    public record ReuseKey(Long versionId,
                           Integer batchNo,
                           String platformCode,
                           Long promptTemplateId,
                           String competitorNameNorm) {
    }

    private static final class ReuseSnapshotBuilder {
        private boolean hasAnalyzeSuccess;
        private PresaleAiCall querySuccessCall;
    }
}
