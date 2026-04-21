package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentLabelMappingTest {

    @Test
    void intentLabelMapping_matchesSpecV3() {
        assertThat(PresaleIntentCode.labelOf("RECOMMENDATION")).isEqualTo("推荐型");
        assertThat(PresaleIntentCode.labelOf("COMPARISON")).isEqualTo("对比型");
        assertThat(PresaleIntentCode.labelOf("INQUIRY")).isEqualTo("问题型");
        assertThat(PresaleIntentCode.labelOf("COGNITIVE")).isEqualTo("认知型");
        assertThat(PresaleIntentCode.labelOf("SCENARIO")).isEqualTo("场景型");
        assertThat(PresaleIntentCode.allInOrder())
                .extracting(PresaleIntentCode::getCode)
                .containsExactly("RECOMMENDATION", "COMPARISON", "INQUIRY", "COGNITIVE", "SCENARIO");
    }
}

