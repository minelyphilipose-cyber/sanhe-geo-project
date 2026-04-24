package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计算 platform_intent_breakdown。
 */
@Component
@RequiredArgsConstructor
public class PlatformIntentBreakdownBuilder {

    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private static final String CATEGORY_COGNITIVE = "COGNITIVE";
    private static final String CATEGORY_COMPARISON = "COMPARISON";
    @Value("${presale.prompt.active-version:v2}")
    private String activePromptTemplateVersion;

    public BuildResult build(Long versionId,
                             RawSnapshotDTO rawSnapshot,
                             ComputedSnapshotDTO computedSnapshot,
                             boolean allowSyntheticFallback) {
        if (rawSnapshot == null || rawSnapshot.getPlatformBreakdown() == null || rawSnapshot.getPlatformBreakdown().isEmpty()) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: platform_breakdown is empty");
        }
        List<PlatformBreakdown> platforms = rawSnapshot.getPlatformBreakdown();
        Map<String, PlatformBreakdown> platformByCode = new HashMap<>();
        for (PlatformBreakdown platform : platforms) {
            if (platform.getPlatformCode() != null) {
                platformByCode.put(platform.getPlatformCode(), platform);
            }
        }

        Map<String, Integer> intentTotalPrompts = resolveIntentTotalPromptsFromTemplate();
        List<PlatformIntentSampleRow> rows = aiPromptResultMapper.selectIntentSamplesByVersionId(versionId);
        List<PlatformIntentJudgeAggregateRow> judgeRows = aiPromptResultMapper.selectJudgeAggregatesByVersionId(versionId);
        if ((rows == null || rows.isEmpty()) && allowSyntheticFallback) {
            List<PlatformIntentCell> cells = buildSyntheticFallback(platforms, intentTotalPrompts);
            return new BuildResult(cells, intentTotalPrompts);
        }

        Map<String, Stat> statByKey = buildSampleStats(rows, platformByCode);
        Map<String, JudgeStat> judgeStatByKey = buildJudgeStats(judgeRows, platformByCode);
        List<PlatformIntentCell> result = new ArrayList<>();
        for (PlatformBreakdown platform : platforms) {
            String platformCode = platform.getPlatformCode();
            for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
                String key = key(platformCode, intent.getCode());
                Integer totalPrompts = intentTotalPrompts.get(intent.getCode());
                if (totalPrompts == null) {
                    throw new BizException(500, "platform_intent_breakdown integrity violated: missing total_prompts for intent=" + intent.getCode());
                }
                result.add(buildCell(platformCode, intent, totalPrompts, statByKey.get(key), judgeStatByKey.get(key)));
            }
        }
        return new BuildResult(result, intentTotalPrompts);
    }

    private Map<String, Stat> buildSampleStats(List<PlatformIntentSampleRow> rows,
                                               Map<String, PlatformBreakdown> platformByCode) {
        Map<String, Stat> statByKey = new HashMap<>();
        if (rows != null) {
            for (PlatformIntentSampleRow row : rows) {
                if (row.getPlatformCode() == null || row.getIntentLabel() == null) {
                    continue;
                }
                PlatformBreakdown platform = platformByCode.get(row.getPlatformCode());
                if (platform == null) {
                    continue;
                }
                PresaleIntentCode intent = resolveIntentByLabel(row.getIntentLabel());
                String key = key(row.getPlatformCode(), intent.getCode());
                Stat stat = statByKey.computeIfAbsent(key, k -> new Stat());
                stat.totalRows++;

                boolean included = PresaleSampleInclusion.isIncluded(
                        row.getCallStatus(),
                        row.getIsExcluded(),
                        Boolean.TRUE.equals(platform.getIsDegraded())
                );
                if (!included) {
                    continue;
                }

                stat.includedRows++;
                if (row.getIsMentioned() != null && row.getIsMentioned() == 1) {
                    stat.mentionedRows++;
                }
            }
        }
        return statByKey;
    }

    private Map<String, JudgeStat> buildJudgeStats(List<PlatformIntentJudgeAggregateRow> rows,
                                                   Map<String, PlatformBreakdown> platformByCode) {
        Map<String, JudgeStat> statByKey = new HashMap<>();
        if (rows == null) {
            return statByKey;
        }
        for (PlatformIntentJudgeAggregateRow row : rows) {
            if (row == null || row.getPlatformCode() == null || row.getCategory() == null) {
                continue;
            }
            if (!platformByCode.containsKey(row.getPlatformCode())) {
                continue;
            }
            String category = row.getCategory();
            if (!CATEGORY_COGNITIVE.equals(category) && !CATEGORY_COMPARISON.equals(category)) {
                continue;
            }
            String key = key(row.getPlatformCode(), category);
            JudgeStat judgeStat = new JudgeStat();
            judgeStat.cellScore = row.getCellScore();
            judgeStat.sampleCount = row.getSampleCount();
            judgeStat.stance = row.getStance();
            statByKey.put(key, judgeStat);
        }
        return statByKey;
    }

    private PlatformIntentCell buildCell(String platformCode,
                                         PresaleIntentCode intent,
                                         Integer totalPrompts,
                                         Stat sampleStat,
                                         JudgeStat judgeStat) {
        Integer samplePromptCount = null;
        int mentionCount = 0;
        if (sampleStat != null && sampleStat.totalRows > 0) {
            samplePromptCount = sampleStat.includedRows;
            mentionCount = sampleStat.includedRows > 0 ? sampleStat.mentionedRows : 0;
        }

        Integer mentionRate = calculateRate(mentionCount, samplePromptCount);
        Integer platformPromptCount = samplePromptCount;
        String stance = null;

        if (isJudgeIntent(intent) && judgeStat != null) {
            // 认知/对比在 PR3 口径切换后由裁判聚合给出 0-100 标量，映射到 mention_rate 字段。
            mentionRate = mapJudgeCellScore(judgeStat.cellScore);
            platformPromptCount = judgeStat.sampleCount;
            // 认知/对比下无意义,始终为 0。
            mentionCount = 0;
            if (PresaleIntentCode.COMPARISON == intent) {
                stance = judgeStat.stance;
            }
        }

        return PlatformIntentCell.builder()
                .platformCode(platformCode)
                .intentCode(intent.getCode())
                .intentLabel(intent.getLabel())
                .mentionCount(mentionCount)
                .mentionRate(mentionRate)
                .totalPrompts(totalPrompts)
                .platformPromptCount(platformPromptCount)
                .stance(stance)
                .build();
    }

    private Map<String, Integer> resolveIntentTotalPromptsFromTemplate() {
        Map<String, Integer> map = new HashMap<>();
        List<PromptTemplateIntentStatRow> rows = aiPromptResultMapper.selectTemplateIntentStats(activePromptTemplateVersion);
        if (rows != null) {
            for (PromptTemplateIntentStatRow row : rows) {
                if (row == null || row.getIntentLabel() == null) {
                    continue;
                }
                PresaleIntentCode intentCode = resolveIntentByLabel(row.getIntentLabel());
                Integer hasCompetitorVar = row.getHasCompetitorVar();
                // 对比型在 v3 下依赖竞品变量模板(has_competitor_var=1)，不能按旧规则剔除。
                // 其余意图仍只计入通用模板(has_competitor_var=0)，保持 scene_coverage 同源口径。
                if (intentCode != PresaleIntentCode.COMPARISON
                        && (hasCompetitorVar == null || hasCompetitorVar != 0)) {
                    continue;
                }
                int base = safeInt(row.getTemplateCount());
                map.merge(intentCode.getCode(), base, Integer::sum);
            }
        }

        for (PresaleIntentCode intentCode : PresaleIntentCode.allInOrder()) {
            if (!map.containsKey(intentCode.getCode())) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: template stats missing category=" + intentCode.getLabel());
            }
        }
        return map;
    }

    private PresaleIntentCode resolveIntentByLabel(String label) {
        try {
            return PresaleIntentCode.fromLabel(label);
        } catch (IllegalArgumentException e) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: unsupported intent label=" + label);
        }
    }

    /**
     * mock 兼容兜底:如果当前环境未写入 presale_ai_prompt_result,按平台提及总量比例分配到各意图。
     */
    private List<PlatformIntentCell> buildSyntheticFallback(List<PlatformBreakdown> platforms,
                                                            Map<String, Integer> intentTotalPrompts) {
        Map<String, Integer> sampleIntentPrompts = new HashMap<>();
        for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
            if (isJudgeIntent(intent)) {
                continue;
            }
            sampleIntentPrompts.put(intent.getCode(), intentTotalPrompts.getOrDefault(intent.getCode(), 0));
        }
        int totalSampleIntentPrompts = sampleIntentPrompts.values().stream().mapToInt(Integer::intValue).sum();
        if (totalSampleIntentPrompts <= 0) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: sample intent total_prompts sum <= 0");
        }

        List<PlatformIntentCell> result = new ArrayList<>();
        for (PlatformBreakdown platform : platforms) {
            int platformMentionTotal = safeInt(platform.getMentionCount());
            if (platformMentionTotal > totalSampleIntentPrompts) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: platform mention_count exceeds total_prompts, platform="
                        + platform.getPlatformCode());
            }

            Map<String, Integer> mentionAllocation = allocateByLargestRemainder(platformMentionTotal, sampleIntentPrompts, totalSampleIntentPrompts);
            for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
                int promptCount = intentTotalPrompts.get(intent.getCode());
                int mentionCount = isJudgeIntent(intent) ? 0 : mentionAllocation.getOrDefault(intent.getCode(), 0);
                int mentionRate = calculateRate(mentionCount, promptCount);
                result.add(PlatformIntentCell.builder()
                        .platformCode(platform.getPlatformCode())
                        .intentCode(intent.getCode())
                        .intentLabel(intent.getLabel())
                        .mentionCount(mentionCount)
                        .mentionRate(mentionRate)
                        .totalPrompts(promptCount)
                        .platformPromptCount(promptCount)
                        .build());
            }
        }
        return result;
    }

    private Map<String, Integer> allocateByLargestRemainder(int total,
                                                            Map<String, Integer> weights,
                                                            int weightSum) {
        Map<String, Integer> allocation = new HashMap<>();
        List<Remainder> remainders = new ArrayList<>();
        int allocated = 0;

        for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
            String code = intent.getCode();
            int weight = weights.getOrDefault(code, 0);
            double exact = weightSum <= 0 ? 0d : (total * weight * 1.0d / weightSum);
            int base = (int) Math.floor(exact);
            allocation.put(code, base);
            allocated += base;
            remainders.add(new Remainder(code, exact - base));
        }

        int rest = total - allocated;
        remainders.sort(Comparator.comparingDouble(Remainder::fraction).reversed());
        for (int i = 0; i < rest && i < remainders.size(); i++) {
            String code = remainders.get(i).code();
            allocation.put(code, allocation.get(code) + 1);
        }
        return allocation;
    }

    private Integer calculateRate(int mentionCount, Integer promptCount) {
        if (promptCount == null || promptCount <= 0) {
            return 0;
        }
        return (int) Math.round(mentionCount * 100.0d / promptCount);
    }

    private Integer mapJudgeCellScore(BigDecimal cellScore) {
        if (cellScore == null) {
            return null;
        }
        return (int) Math.round(cellScore.doubleValue());
    }

    private boolean isJudgeIntent(PresaleIntentCode intent) {
        return intent == PresaleIntentCode.COGNITIVE || intent == PresaleIntentCode.COMPARISON;
    }

    private String key(String platformCode, String intentCode) {
        return platformCode + "::" + intentCode;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static class Stat {
        int totalRows;
        int includedRows;
        int mentionedRows;
    }

    private static class JudgeStat {
        BigDecimal cellScore;
        Integer sampleCount;
        String stance;
    }

    public record BuildResult(List<PlatformIntentCell> cells,
                              Map<String, Integer> intentTotalPrompts) {
    }

    private record Remainder(String code, double fraction) {
    }
}
