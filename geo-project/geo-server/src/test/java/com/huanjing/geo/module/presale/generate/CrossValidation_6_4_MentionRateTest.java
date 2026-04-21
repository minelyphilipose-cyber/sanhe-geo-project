package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrossValidation_6_4_MentionRateTest {

    @Test
    void validate_throwsWhenMentionRateFormulaMismatch() {
        PlatformIntentBreakdownValidator validator = new PlatformIntentBreakdownValidator();
        List<PlatformBreakdown> platforms = List.of(platform("P1", 2));
        List<IntentBreakdown> intents = intents();
        List<PlatformIntentCell> cells = validCells("P1");
        cells.get(0).setMentionCount(1);
        cells.get(0).setPlatformPromptCount(8);
        cells.get(0).setMentionRate(12); // expected 13
        cells.get(1).setMentionCount(1);

        assertThatThrownBy(() -> validator.validate(platforms, intents, cells))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("mention_rate formula mismatch");
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

    private List<PlatformIntentCell> validCells(String platformCode) {
        List<PlatformIntentCell> list = new ArrayList<>();
        for (PresaleIntentCode code : PresaleIntentCode.allInOrder()) {
            list.add(PlatformIntentCell.builder()
                    .platformCode(platformCode)
                    .intentCode(code.getCode())
                    .intentLabel(code.getLabel())
                    .mentionCount(0)
                    .mentionRate(0)
                    .totalPrompts(10)
                    .platformPromptCount(10)
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

