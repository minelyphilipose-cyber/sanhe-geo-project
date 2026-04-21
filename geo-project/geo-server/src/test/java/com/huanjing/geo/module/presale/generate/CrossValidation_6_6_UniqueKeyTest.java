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

class CrossValidation_6_6_UniqueKeyTest {

    @Test
    void validate_throwsWhenDuplicatePairFound() {
        PlatformIntentBreakdownValidator validator = new PlatformIntentBreakdownValidator();
        List<PlatformBreakdown> platforms = List.of(platform("P1", 0));
        List<IntentBreakdown> intents = intents();
        List<PlatformIntentCell> cells = validCells("P1");
        cells.get(1).setIntentCode("RECOMMENDATION");
        cells.get(1).setIntentLabel("推荐型");

        assertThatThrownBy(() -> validator.validate(platforms, intents, cells))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("duplicate pair");
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

