package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.generate.web.PresaleSearchEvidence;
import com.huanjing.geo.module.presale.generate.web.PresaleWebExecutionContext;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PresaleSampleStatisticsService {
    private static final String QUERY = "QUERY";
    private static final String ANALYZE = "ANALYZE";
    private static final String SUCCESS = "SUCCESS";
    private static final String SKIPPED_DEGRADED = "SKIPPED_DEGRADED";

    private final PresaleAiCallMapper aiCallMapper;
    private final PresaleAiPromptResultMapper promptResultMapper;
    private final PresaleReportVersionMapper versionMapper;
    private final PresaleReportVersionPromptTemplateMapper templateMapper;
    private final ReuseDecisionService reuseDecisionService;
    private final ObjectMapper objectMapper;

    public StatisticsResult classifyAndPersist(Long versionId,
                                               PresaleWebExecutionContext context,
                                               Set<String> degradedPlatforms,
                                               String competitorGroupName) {
        if (context == null || !context.mode().requiresWebQuery()) {
            markLegacy(versionId);
            return StatisticsResult.legacy();
        }
        Set<String> reportPlatformCodes = context.reportPlatforms().stream()
                .map(com.huanjing.geo.module.system.entity.AiPlatformConfig::getPlatformCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<ReuseDecisionService.ReuseKey> plannedKeys = buildPlannedKeys(
                versionId, reportPlatformCodes, competitorGroupName);
        Map<ReuseDecisionService.ReuseKey, List<PresaleAiCall>> callsByKey = loadCalls(versionId);
        Set<String> degraded = degradedPlatforms == null ? Set.of() : degradedPlatforms;
        Set<ReuseDecisionService.ReuseKey> effective = new LinkedHashSet<>();
        Map<String, Integer> failureCodeCounts = new HashMap<>();
        int skipped = 0;
        int queryFailed = 0;
        int analyzeFailed = 0;
        int degradedExcluded = 0;
        int plannedWeb = 0;
        int webValid = 0;

        for (ReuseDecisionService.ReuseKey key : plannedKeys) {
            List<PresaleAiCall> calls = callsByKey.getOrDefault(key, List.of());
            List<PresaleAiCall> queryCalls = calls.stream().filter(c -> QUERY.equals(c.getStage())).toList();
            boolean webQuery = context.usesWebQuery(key.platformCode());
            if (webQuery) {
                plannedWeb++;
            }
            PresaleAiCall validQuery = queryCalls.stream()
                    .filter(c -> SUCCESS.equals(c.getCallStatus()))
                    .filter(c -> webQuery
                            ? reuseDecisionService.isReusableWebQuery(c, context.identity(key.platformCode()))
                            : c.getQueryContractVersion() == null || c.getQueryContractVersion().isBlank())
                    .findFirst().orElse(null);
            if (validQuery == null) {
                boolean explicitSkip = queryCalls.stream().anyMatch(c -> SKIPPED_DEGRADED.equals(c.getCallStatus()));
                if (explicitSkip) {
                    skipped++;
                } else {
                    queryFailed++;
                    if (webQuery) {
                        String code = mainFailureCode(queryCalls);
                        failureCodeCounts.merge(code, 1, Integer::sum);
                    }
                }
                continue;
            }
            if (webQuery) {
                if (reuseDecisionService.hasValidWebSearchEvidence(
                        validQuery, context.identity(key.platformCode()))) {
                    webValid++;
                }
            }
            boolean analyzeSuccess = calls.stream()
                    .anyMatch(c -> ANALYZE.equals(c.getStage())
                            && SUCCESS.equals(c.getCallStatus())
                            && validQuery.getId() != null
                            && validQuery.getId().equals(c.getParentCallId()));
            if (!analyzeSuccess) {
                analyzeFailed++;
            } else if (degraded.contains(key.platformCode())) {
                degradedExcluded++;
            } else {
                effective.add(key);
            }
        }
        int planned = plannedKeys.size();
        int classified = skipped + queryFailed + analyzeFailed + degradedExcluded + effective.size();
        if (planned != classified) {
            throw new IllegalStateException("Presale sample classification invariant failed: planned="
                    + planned + ", classified=" + classified);
        }

        updateEffectiveFlags(versionId, effective);
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setPlannedQueryCount(planned);
        update.setPlannedWebQueryCount(plannedWeb);
        update.setWebValidQueryCount(webValid);
        update.setEffectiveSampleCount(effective.size());
        update.setQueryFailedCount(queryFailed);
        update.setAnalyzeFailedCount(analyzeFailed);
        update.setSkippedQueryCount(skipped);
        update.setDegradedExcludedSampleCount(degradedExcluded);
        update.setMainWebFailureCode(failureCodeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).findFirst().orElse(null));
        versionMapper.updateById(update);
        return new StatisticsResult(planned, plannedWeb, webValid, effective.size(), queryFailed, analyzeFailed,
                skipped, degradedExcluded, new EffectiveSampleIndex(effective));
    }

    private Set<ReuseDecisionService.ReuseKey> buildPlannedKeys(Long versionId,
                                                                Set<String> platformCodes,
                                                                String competitorGroupName) {
        List<PresaleReportVersionPromptTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<PresaleReportVersionPromptTemplate>()
                        .eq(PresaleReportVersionPromptTemplate::getReportVersionId, versionId));
        Set<ReuseDecisionService.ReuseKey> keys = new LinkedHashSet<>();
        for (String platformCode : platformCodes) {
            for (PresaleReportVersionPromptTemplate template : templates) {
                if (Integer.valueOf(0).equals(template.getHasCompetitorVar())) {
                    keys.add(reuseDecisionService.keyOf(versionId, 1, platformCode, template.getId(), ""));
                } else if (competitorGroupName != null && !competitorGroupName.isBlank()) {
                    keys.add(reuseDecisionService.keyOf(versionId, 2, platformCode,
                            template.getId(), competitorGroupName));
                }
            }
        }
        return keys;
    }

    private Map<ReuseDecisionService.ReuseKey, List<PresaleAiCall>> loadCalls(Long versionId) {
        List<PresaleAiCall> calls = aiCallMapper.selectList(new LambdaQueryWrapper<PresaleAiCall>()
                .eq(PresaleAiCall::getVersionId, versionId)
                .in(PresaleAiCall::getStage, List.of(QUERY, ANALYZE))
                .orderByDesc(PresaleAiCall::getId));
        Map<ReuseDecisionService.ReuseKey, List<PresaleAiCall>> grouped = new HashMap<>();
        for (PresaleAiCall call : calls) {
            ReuseDecisionService.ReuseKey key = reuseDecisionService.keyOf(call.getVersionId(), call.getBatchNo(),
                    call.getPlatformCode(), call.getPromptTemplateId(), call.getCompetitorName());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(call);
        }
        return grouped;
    }

    private void updateEffectiveFlags(Long versionId, Set<ReuseDecisionService.ReuseKey> effective) {
        promptResultMapper.update(null, new UpdateWrapper<PresaleAiPromptResult>()
                .eq("version_id", versionId)
                .set("effective_sample", false));
        List<PresaleAiPromptResult> rows = promptResultMapper.selectList(
                new LambdaQueryWrapper<PresaleAiPromptResult>()
                        .eq(PresaleAiPromptResult::getVersionId, versionId));
        List<Long> effectiveIds = rows.stream()
                .filter(row -> effective.contains(reuseDecisionService.keyOf(row.getVersionId(), row.getBatchNo(),
                        row.getPlatformCode(), row.getPromptTemplateId(), row.getCompetitorName())))
                .map(PresaleAiPromptResult::getId).toList();
        if (!effectiveIds.isEmpty()) {
            promptResultMapper.update(null, new UpdateWrapper<PresaleAiPromptResult>()
                    .in("id", effectiveIds)
                    .set("effective_sample", true));
        }
    }

    private void markLegacy(Long versionId) {
        promptResultMapper.update(null, new UpdateWrapper<PresaleAiPromptResult>()
                .eq("version_id", versionId)
                .set("effective_sample", true));
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setPlannedQueryCount(0);
        update.setPlannedWebQueryCount(0);
        update.setWebValidQueryCount(0);
        update.setEffectiveSampleCount(0);
        update.setQueryFailedCount(0);
        update.setAnalyzeFailedCount(0);
        update.setSkippedQueryCount(0);
        update.setDegradedExcludedSampleCount(0);
        update.setMainWebFailureCode(null);
        versionMapper.updateById(update);
    }

    private String mainFailureCode(List<PresaleAiCall> queryCalls) {
        for (PresaleAiCall call : queryCalls) {
            if (!PresaleSearchEvidence.CONTRACT_VERSION.equals(call.getQueryContractVersion())
                    || call.getSearchEvidenceJson() == null) continue;
            try {
                JsonNode node = objectMapper.readTree(call.getSearchEvidenceJson());
                String code = node.path("failureCode").asText(null);
                if (code != null && code.matches("^[A-Z0-9_]{1,64}$")) return code;
            } catch (Exception ignored) { }
        }
        return "QUERY_FAILED";
    }

    public record StatisticsResult(int planned, int plannedWeb, int webValid, int effective,
                                   int queryFailed, int analyzeFailed, int skipped,
                                   int degradedExcluded, EffectiveSampleIndex effectiveSampleIndex) {
        static StatisticsResult legacy() {
            return new StatisticsResult(0, 0, 0, 0, 0, 0, 0, 0, new EffectiveSampleIndex(Set.of()));
        }
    }
}
