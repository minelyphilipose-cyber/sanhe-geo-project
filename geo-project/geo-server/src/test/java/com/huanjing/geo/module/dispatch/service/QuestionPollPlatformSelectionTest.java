package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionPollPlatformSelectionTest {

    @Test
    void enabledWebWinsAndNativeIsUsedWhenWebIsDisabled() {
        AiPlatformConfig kimi = row(1L, "kimi", "kimi", "STANDARD_CHAT",
                IntegrationType.OPENAI_CHAT, true, true);
        AiPlatformConfig kimiWebDisabled = row(2L, "kimi_web", "kimi", "QUESTION_POLL_WEB",
                IntegrationType.MIMO_CHAT_WEB, false, false);
        AiPlatformConfig mimo = row(3L, "mimo", "mimo", "STANDARD_CHAT",
                IntegrationType.OPENAI_CHAT, true, true);
        AiPlatformConfig mimoWeb = row(4L, "mimo_web", "mimo", "QUESTION_POLL_WEB",
                IntegrationType.MIMO_CHAT_WEB, true, true);

        List<AiPlatformConfig> selected = QuestionPollPlatformSelection.preferredEnabled(
                List.of(kimiWebDisabled, mimo, kimi, mimoWeb));

        assertEquals(List.of("kimi", "mimo_web"),
                selected.stream().map(AiPlatformConfig::getPlatformCode).toList());
    }

    private AiPlatformConfig row(Long id, String code, String channel, String scene,
                                 IntegrationType type, boolean enabled, boolean pollEnabled) {
        AiPlatformConfig row = new AiPlatformConfig();
        row.setId(id);
        row.setPlatformCode(code);
        row.setChannelCode(channel);
        row.setUsageScene(scene);
        row.setIntegrationType(type.name());
        row.setPriorityLevel("P1");
        row.setEnabled(enabled);
        row.setEnabledForQuestionPoll(pollEnabled);
        return row;
    }
}
