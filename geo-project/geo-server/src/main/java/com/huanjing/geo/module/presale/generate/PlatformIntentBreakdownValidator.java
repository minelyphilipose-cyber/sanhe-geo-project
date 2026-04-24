package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * platform_intent_breakdown 完整性校验器(新 DONE 写入前执行)。
 */
@Component
public class PlatformIntentBreakdownValidator {
    private static final Set<PresaleIntentCode> MENTION_RATE_INTENTS = EnumSet.of(
            PresaleIntentCode.RECOMMENDATION,
            PresaleIntentCode.INQUIRY,
            PresaleIntentCode.SCENARIO
    );
    private static final Set<String> VALID_STANCE = Set.of("target", "tie", "competitor");

    public void validate(List<PlatformBreakdown> platformBreakdown,
                         List<IntentBreakdown> intentBreakdown,
                         List<PlatformIntentCell> cells) {
        if (platformBreakdown == null || intentBreakdown == null || cells == null) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: input is null");
        }

        Map<String, Integer> expectedTotalPromptsByCode = resolveExpectedTotalPrompts(intentBreakdown);

        // §6.5 条数断言
        int expectedLength = platformBreakdown.size() * PresaleIntentCode.allInOrder().size();
        if (cells.size() != expectedLength) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: length mismatch expected="
                    + expectedLength + ", actual=" + cells.size());
        }

        // §6.6 唯一性断言 + §6.4 mention_rate + §6.3 total_prompts
        Map<String, Integer> seen = new HashMap<>();
        Map<String, Integer> platformMentionSums = new HashMap<>();
        for (PlatformIntentCell cell : cells) {
            if (cell.getPlatformCode() == null || cell.getIntentCode() == null) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: platform_code/intent_code is null");
            }
            String pairKey = pairKey(cell.getPlatformCode(), cell.getIntentCode());
            if (seen.put(pairKey, 1) != null) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: duplicate pair " + pairKey);
            }

            String expectedLabel = PresaleIntentCode.labelOf(cell.getIntentCode());
            if (!expectedLabel.equals(cell.getIntentLabel())) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: intent_label mismatch code="
                        + cell.getIntentCode() + ", label=" + cell.getIntentLabel());
            }

            PresaleIntentCode intentCode = PresaleIntentCode.fromCode(cell.getIntentCode());
            validateRate(cell, intentCode);

            Integer expectedTotalPrompts = expectedTotalPromptsByCode.get(cell.getIntentCode());
            if (expectedTotalPrompts == null) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: unknown intent_code=" + cell.getIntentCode());
            }
            if (!expectedTotalPrompts.equals(cell.getTotalPrompts())) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: total_prompts mismatch code="
                        + cell.getIntentCode() + ", expected=" + expectedTotalPrompts + ", actual=" + cell.getTotalPrompts());
            }

            if (MENTION_RATE_INTENTS.contains(intentCode)) {
                platformMentionSums.merge(cell.getPlatformCode(), safeInt(cell.getMentionCount()), Integer::sum);
            }
        }

        // 组合完整性:每个平台必须有 5 个 intent
        for (PlatformBreakdown pb : platformBreakdown) {
            for (PresaleIntentCode intentCode : PresaleIntentCode.allInOrder()) {
                String key = pairKey(pb.getPlatformCode(), intentCode.getCode());
                if (!seen.containsKey(key)) {
                    throw new BizException(500, "platform_intent_breakdown integrity violated: missing pair " + key);
                }
            }
        }

        // §6.2 平台守恒律(仅推荐/问题/场景口径)
        for (PlatformBreakdown pb : platformBreakdown) {
            int expected = safeInt(pb.getMentionCount());
            int actual = platformMentionSums.getOrDefault(pb.getPlatformCode(), 0);
            if (expected != actual) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: platform mention sum mismatch platform="
                        + pb.getPlatformCode() + ", expected=" + expected + ", actual=" + actual);
            }
        }

    }

    private void validateRate(PlatformIntentCell cell, PresaleIntentCode intentCode) {
        int mentionCount = safeInt(cell.getMentionCount());
        Integer mentionRate = cell.getMentionRate();
        Integer promptCount = cell.getPlatformPromptCount();

        if (mentionRate != null && (mentionRate < 0 || mentionRate > 100)) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: mention_rate out of range, pair="
                    + pairKey(cell.getPlatformCode(), cell.getIntentCode()));
        }

        if (intentCode == PresaleIntentCode.COMPARISON) {
            String stance = cell.getStance();
            if (stance != null && !VALID_STANCE.contains(stance)) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: invalid stance, pair="
                        + pairKey(cell.getPlatformCode(), cell.getIntentCode()));
            }
        } else if (intentCode == PresaleIntentCode.COGNITIVE && cell.getStance() != null) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: stance must be null for cognitive, pair="
                    + pairKey(cell.getPlatformCode(), cell.getIntentCode()));
        }

        if (!MENTION_RATE_INTENTS.contains(intentCode)) {
            return;
        }

        if (promptCount == null || promptCount <= 0) {
            if (safeInt(mentionRate) != 0 || mentionCount != 0) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: prompt_count<=0 but mention/rate not zero, pair="
                        + pairKey(cell.getPlatformCode(), cell.getIntentCode()));
            }
            return;
        }

        if (mentionCount > promptCount) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: mention_count > platform_prompt_count, pair="
                    + pairKey(cell.getPlatformCode(), cell.getIntentCode()));
        }
        int expectedRate = (int) Math.round(mentionCount * 100.0d / promptCount);
        if (safeInt(mentionRate) != expectedRate) {
            throw new BizException(500, "platform_intent_breakdown integrity violated: mention_rate formula mismatch, pair="
                    + pairKey(cell.getPlatformCode(), cell.getIntentCode()) + ", expected=" + expectedRate + ", actual=" + safeInt(mentionRate));
        }
    }

    private Map<String, Integer> resolveExpectedTotalPrompts(List<IntentBreakdown> intentBreakdown) {
        Map<String, Integer> map = new HashMap<>();
        for (IntentBreakdown item : intentBreakdown) {
            if (item == null || item.getCategory() == null) {
                continue;
            }
            PresaleIntentCode code;
            try {
                code = PresaleIntentCode.fromLabel(item.getCategory());
            } catch (IllegalArgumentException e) {
                continue;
            }
            map.put(code.getCode(), safeInt(item.getTotalPrompts()));
        }
        for (PresaleIntentCode code : PresaleIntentCode.allInOrder()) {
            if (!map.containsKey(code.getCode())) {
                throw new BizException(500, "platform_intent_breakdown integrity violated: intent_breakdown missing " + code.getCode());
            }
        }
        return map;
    }

    private String pairKey(String platformCode, String intentCode) {
        return platformCode + "::" + intentCode;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
