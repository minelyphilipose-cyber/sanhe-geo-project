package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordLlmQuestionServiceTest {

    private final KeywordLlmQuestionService service = new KeywordLlmQuestionService(
            null, null, null, null, null, null, null
    );

    @Test
    void parseSeed_trimsSingleSeed() {
        String seed = service.parseSeed(" 合肥小吃 ");

        assertEquals("合肥小吃", seed);
    }

    @Test
    void parseSeed_rejectsBlankSeed() {
        BizException ex = assertThrows(BizException.class,
                () -> service.parseSeed(" "));

        assertTrue(ex.getMessage().startsWith("LLM_SEED_INVALID_COUNT:"));
    }

    @Test
    void parseSeed_rejectsSeedOverTenChars() {
        BizException ex = assertThrows(BizException.class,
                () -> service.parseSeed("一二三四五六七八九十一"));

        assertTrue(ex.getMessage().startsWith("LLM_SEED_TOO_LONG:"));
    }
}
