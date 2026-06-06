package com.huanjing.geo.module.presale.generate.narrative;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndustryKeyNormalizerTest {

    @Test
    void normalizeConservativelyWithoutSemanticMerge() {
        assertEquals("口腔医疗", IndustryKeyNormalizer.normalize(" 口腔医疗 "));
        assertEquals("牙科", IndustryKeyNormalizer.normalize("牙科行业"));
        assertEquals("齿科", IndustryKeyNormalizer.normalize("齿科服务"));
    }

    @Test
    void preserveAllFallbackKey() {
        assertEquals("_all_", IndustryKeyNormalizer.normalize("_ALL_"));
    }
}
