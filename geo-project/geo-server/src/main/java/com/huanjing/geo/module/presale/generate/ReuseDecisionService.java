package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.web.CompanionIdentity;
import com.huanjing.geo.module.presale.generate.web.PresaleEvidenceLevel;
import com.huanjing.geo.module.presale.generate.web.PresaleQueryWebMode;
import com.huanjing.geo.module.presale.generate.web.PresaleSearchEvidence;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReuseDecisionService {

    private final PresaleAiCallMapper aiCallMapper;
    private final ObjectMapper objectMapper;

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
                if (call.getParentCallId() != null) {
                    builder.successfulAnalyzeParentIds.add(call.getParentCallId());
                }
                continue;
            }
            if ("QUERY".equals(call.getStage()) && "SUCCESS".equals(call.getCallStatus()) && builder.querySuccessCall == null) {
                builder.querySuccessCall = call;
            }
        }
        Map<ReuseKey, ReuseSnapshot> snapshots = new HashMap<>();
        builders.forEach((k, v) -> {
            boolean hasAnalyzeSuccess = v.querySuccessCall != null
                    && v.querySuccessCall.getId() != null
                    && v.successfulAnalyzeParentIds.contains(v.querySuccessCall.getId());
            snapshots.put(k, new ReuseSnapshot(hasAnalyzeSuccess, v.querySuccessCall));
        });
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

    /** Native QUERY in a mixed REQUIRED run must not reuse a historical WEB_SEARCH_V1 answer. */
    public ReuseDecision decideNative(PlatformCallContext ctx, Map<ReuseKey, ReuseSnapshot> cache) {
        ReuseSnapshot snapshot = cache.get(keyOf(ctx));
        if (snapshot == null || !isReusableNativeQuery(snapshot.querySuccessCall())) {
            return ReuseDecision.RUN_FULL;
        }
        return snapshot.hasAnalyzeSuccess() ? ReuseDecision.SKIP_ALL : ReuseDecision.REUSE_QUERY_ONLY;
    }

    /** REQUIRED mode reuses only the same companion execution contract; search coverage is separate. */
    public ReuseDecision decide(PlatformCallContext ctx,
                                Map<ReuseKey, ReuseSnapshot> cache,
                                PresaleQueryWebMode mode,
                                CompanionIdentity currentCompanion) {
        if (mode == null || !mode.requiresWebQuery() || currentCompanion == null) {
            return decide(ctx, cache);
        }
        ReuseSnapshot snapshot = cache.get(keyOf(ctx));
        if (snapshot == null || !isReusableWebQuery(snapshot.querySuccessCall(), currentCompanion)) {
            return ReuseDecision.RUN_FULL;
        }
        return snapshot.hasAnalyzeSuccess() ? ReuseDecision.SKIP_ALL : ReuseDecision.REUSE_QUERY_ONLY;
    }

    public boolean isReusableWebQuery(PresaleAiCall call, CompanionIdentity currentCompanion) {
        if (call == null || currentCompanion == null
                || !PresaleSearchEvidence.CONTRACT_VERSION.equals(call.getQueryContractVersion())
                || call.getSearchEvidenceJson() == null || call.getSearchEvidenceJson().isBlank()) {
            return false;
        }
        try {
            JsonNode evidence = objectMapper.readTree(call.getSearchEvidenceJson());
            return "SUCCEEDED".equals(evidence.path("searchStatus").asText())
                    && currentCompanion.configId().equals(evidence.path("webConfigId").longValue())
                    && currentCompanion.configVersion().equals(evidence.path("webConfigVersion").longValue())
                    && currentCompanion.integrationType().name().equals(evidence.path("integrationType").asText())
                    && currentCompanion.modelId().equals(evidence.path("modelId").asText());
        } catch (Exception ex) {
            return false;
        }
    }

    /** A successful answer can be reused without claiming that Web Search produced usable evidence. */
    public boolean hasValidWebSearchEvidence(PresaleAiCall call,
                                             CompanionIdentity currentCompanion) {
        if (!isReusableWebQuery(call, currentCompanion)) {
            return false;
        }
        try {
            JsonNode evidence = objectMapper.readTree(call.getSearchEvidenceJson());
            return evidence.path("searchTriggered").asBoolean(false)
                    && !PresaleEvidenceLevel.NONE.name().equals(
                    evidence.path("evidenceLevel").asText());
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isReusableNativeQuery(PresaleAiCall call) {
        return call != null && (call.getQueryContractVersion() == null
                || call.getQueryContractVersion().isBlank());
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
        private final Set<Long> successfulAnalyzeParentIds = new HashSet<>();
        private PresaleAiCall querySuccessCall;
    }
}
