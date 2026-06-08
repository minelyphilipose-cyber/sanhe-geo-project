package com.huanjing.geo.common.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmModelConfigTest {

    @Test
    void kimiTemperature_isAlwaysOne() {
        assertEquals(1D, config("kimi", 0D).temperature());
        assertEquals(1D, config("KIMI", 0.7D).temperature());
        assertEquals(1D, config(" kimi ", null).temperature());
    }

    @Test
    void nonKimiTemperature_keepsExistingBehavior() {
        assertEquals(0D, config("doubao", null).temperature());
        assertEquals(0D, config("doubao", 0D).temperature());
        assertEquals(0.7D, config("doubao", 0.7D).temperature());
    }

    private LlmModelConfig config(String platformCode, Double temperature) {
        return new LlmModelConfig(
                platformCode,
                "Platform",
                "model",
                "Model",
                "https://example.com/v1",
                "key",
                "system",
                temperature,
                10_000,
                30_000,
                0,
                1,
                null,
                false
        );
    }
}
