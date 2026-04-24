package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        if ((rows == null || rows.isEmpty()) && allowSyntheticFallback) {
            List<PlatformIntentCell> cells = buildSyntheticFallback(platforms, intentTotalPrompts);
            return new BuildResult(cells, intentTotalPrompts);
        }

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

        List<PlatformIntentCell> result = new ArrayList<>();
        for (PlatformBreakdown platform : platforms) {
            String platformCode = platform.getPlatformCode();
            for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
                String key = key(platformCode, intent.getCode());
                Stat stat = statByKey.get(key);
                Integer promptCount = null;
                int mentionCount = 0;
                if (stat != null && stat.totalRows > 0) {
                    promptCount = stat.includedRows;
                    mentionCount = stat.includedRows > 0 ? stat.mentionedRows : 0;
                }
                int mentionRate = calculateRate(mentionCount, promptCount);
                Integer totalPrompts = intentTotalPrompts.get(intent.getCode());
                if (totalPrompts == null) {
                    throw new BizException(500, "platform_intent_breakdown integrity violated: missing total_prompts for intent=" + intent.getCode());
                }
                result.add(PlatformIntentCell.builder()
                        .platformCode(platformCode)
                        .intentCode(intent.getCode())
                        .intentLabel(intent.getLabel())
                        .mentionCount(mentionCount)
                        .mentionRate(mentionRate)
                        .totalPrompts(totalPrompts)
                        .platformPromptCount(promptCount)
                        .build());
            }
        }
        return new BuildResult(result, intentTotalPrompts);
    }

    private Map<String, Integer> resolveIntentTotalPromptsFromTemplate() {
        Map<String, Integer> map = new HashMap<>();
        List<PromptTemplateIntentStatRow> rows = aiPromptResultMapper.selectTemplateIntentStats();
        if (rows != null) {
            for (PromptTemplateIntentStatRow row : rows) {
                if (row == null || row.getIntentLabel() == null) {
                    continue;
                }
                // D26 架构让步(PR-3.D3 CP5 Block 2):SQL 层 selectTemplateIntentStats
                // 不再过滤 has_competitor_var,返回全量 GROUP BY 结果。此处 Java 层单点
                // 过滤为唯一屏障,删除此过滤会导致 intent_breakdown 混入竞品模板,
                // 破坏 scene_coverage 同源语义。修改前先查 snapshot §D26 调整记录。
                if (row.getHasCompetitorVar() == null || row.getHasCompetitorVar() != 0) {
                    continue;
                }
                PresaleIntentCode intentCode = resolveIntentByLabel(row.getIntentLabel());
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
        int totalIntentPrompts = intentTotalPrompts.values().stream().mapToInt(Integer::intValue).sum();
        if (totalIntentPrompts <= 0) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: intent total_prompts sum <= 0");
        }

        List<PlatformIntentCell> result = new ArrayList<>();
        for (PlatformBreakdown platform : platforms) {
            int platformMentionTotal = safeInt(platform.getMentionCount());
            if (platformMentionTotal > totalIntentPrompts) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: platform mention_count exceeds total_prompts, platform="
                        + platform.getPlatformCode());
            }

            Map<String, Integer> mentionAllocation = allocateByLargestRemainder(platformMentionTotal, intentTotalPrompts, totalIntentPrompts);
            for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
                int promptCount = intentTotalPrompts.get(intent.getCode());
                int mentionCount = mentionAllocation.getOrDefault(intent.getCode(), 0);
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

    private int calculateRate(int mentionCount, Integer promptCount) {
        if (promptCount == null || promptCount <= 0) {
            return 0;
        }
        return (int) Math.round(mentionCount * 100.0d / promptCount);
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

    public record BuildResult(List<PlatformIntentCell> cells,
                              Map<String, Integer> intentTotalPrompts) {
    }

    private record Remainder(String code, double fraction) {
    }
}
