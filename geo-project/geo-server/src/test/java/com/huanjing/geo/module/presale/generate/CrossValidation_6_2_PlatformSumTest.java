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
        List<PlatformIntentCell> cells = cells("P1", 2, 9, 8); // judge intents 任意值不参与守恒

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
            int mention;
            if (code == PresaleIntentCode.COGNITIVE) {
                mention = cognitiveMention;
            } else if (code == PresaleIntentCode.COMPARISON) {
                mention = comparisonMention;
            } else {
                mention = left > 0 ? 1 : 0;
                left -= mention;
            }
            int promptCount = code == PresaleIntentCode.COGNITIVE ? 7 : (code == PresaleIntentCode.COMPARISON ? 17 : 10);
            int rate = code == PresaleIntentCode.COGNITIVE ? 71
                    : (code == PresaleIntentCode.COMPARISON ? 47 : (mention > 0 ? 10 : 0));
            list.add(PlatformIntentCell.builder()
                    .platformCode(platformCode)
                    .intentCode(code.getCode())
                    .intentLabel(code.getLabel())
                    .mentionCount(mention)
                    .mentionRate(rate)
                    .totalPrompts(10)
                    .platformPromptCount(promptCount)
                    .stance(code == PresaleIntentCode.COMPARISON ? "target" : null)
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
