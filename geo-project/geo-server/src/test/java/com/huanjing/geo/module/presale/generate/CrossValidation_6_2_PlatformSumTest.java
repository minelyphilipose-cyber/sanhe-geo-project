package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrossValidation_6_2_PlatformSumTest {

    @Test
    void validate_throwsWhenPlatformMentionSumMismatchForSampleIntents() {
        PlatformIntentBreakdownValidator validator = new PlatformIntentBreakdownValidator();
        List<PlatformBreakdown> platforms = List.of(platform("P1", 3));
        List<IntentBreakdown> intents = intents();
        List<PlatformIntentCell> cells = cells("P1", 2, 0, 0); // sample sum 2 != 3

        assertThatThrownBy(() -> validator.validate(platforms, intents, cells))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("platform mention sum mismatch");
    }

    @Test
    void validate_skipsSumCheckForJudgeIntents() {
        PlatformIntentBreakdownValidator validator = new PlatformIntentBreakdownValidator();
        List<PlatformBreakdown> platforms = List.of(platform("P1", 2));
        List<IntentBreakdown> intents = intents();
        List<PlatformIntentCell> cells = cells("P1", 2, 71, 47); // judge intents 用判分口径,不参与 mention 守恒

        assertThatCode(() -> validator.validate(platforms, intents, cells)).doesNotThrowAnyException();
    }

    private List<IntentBreakdown> intents() {
        List<IntentBreakdown> list = new ArrayList<>();
        for (PresaleIntentCode code : PresaleIntentCode.allInOrder()) {
            IntentBreakdown i = new IntentBreakdown();
            i.setCategory(code.getLabel());
            i.setTotalPrompts(10);
            list.add(i);
        }
        return list;
    }

    private List<PlatformIntentCell> cells(String platformCode, int sampleMentionTotal, int cognitiveMention, int comparisonMention) {
        List<PlatformIntentCell> list = new ArrayList<>();
        int left = sampleMentionTotal;
        for (PresaleIntentCode code : PresaleIntentCode.allInOrder()) {
            boolean judgeIntent = code == PresaleIntentCode.COGNITIVE || code == PresaleIntentCode.COMPARISON;
            int mention;
            if (code == PresaleIntentCode.COGNITIVE) {
                mention = 0;
            } else if (code == PresaleIntentCode.COMPARISON) {
                mention = 0;
            } else {
                mention = left > 0 ? 1 : 0;
                left -= mention;
            }
            int promptCount = code == PresaleIntentCode.COGNITIVE ? 7 : (code == PresaleIntentCode.COMPARISON ? 17 : 10);
            Integer judgeScore = null;
            if (code == PresaleIntentCode.COGNITIVE) {
                judgeScore = cognitiveMention;
            } else if (code == PresaleIntentCode.COMPARISON) {
                judgeScore = comparisonMention;
            }
            list.add(PlatformIntentCell.builder()
                    .platformCode(platformCode)
                    .intentCode(code.getCode())
                    .intentLabel(code.getLabel())
                    .mentionCount(mention)
                    .mentionRate(judgeIntent ? null : (mention > 0 ? 10 : 0))
                    .judgeScore(judgeScore)
                    .totalPrompts(10)
                    .platformPromptCount(promptCount)
                    .judgeSampleCount(judgeIntent ? promptCount : null)
                    .judgeStance(code == PresaleIntentCode.COMPARISON ? "target" : null)
                    .build());
        }
        return list;
    }

    private PlatformBreakdown platform(String code, int mentionCount) {
        PlatformBreakdown p = new PlatformBreakdown();
        p.setPlatformCode(code);
        p.setMentionCount(mentionCount);
        return p;
    }
}
