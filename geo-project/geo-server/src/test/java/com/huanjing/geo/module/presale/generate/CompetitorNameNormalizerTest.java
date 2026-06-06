package com.huanjing.geo.module.presale.generate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompetitorNameNormalizerTest {

    private final CompetitorNameNormalizer normalizer = new CompetitorNameNormalizer();

    @Test
    void matchDisplayName_mergesRegionAndGenericSuffixAliases() {
        Optional<String> matched = normalizer.matchDisplayName(
                "阜阳美奥口腔医院",
                List.of("阜阳市人民医院口腔科", "美奥口腔", "阜阳市口腔医院")
        );

        assertEquals(Optional.of("美奥口腔"), matched);
    }

    @Test
    void matchDisplayName_keepsFirstAndSecondPeopleHospitalSeparate() {
        List<String> candidates = List.of("阜阳市人民医院口腔科", "阜阳市第二人民医院口腔科", "美奥口腔");

        assertEquals(Optional.of("阜阳市人民医院口腔科"),
                normalizer.matchDisplayName("阜阳市人民医院口腔科", candidates));
        assertEquals(Optional.of("阜阳市第二人民医院口腔科"),
                normalizer.matchDisplayName("阜阳市第二人民医院", candidates));
    }

    @Test
    void matchDisplayName_returnsEmptyWhenCoreIsAmbiguous() {
        Optional<String> matched = normalizer.matchDisplayName(
                "人民医院",
                List.of("阜阳市人民医院口腔科", "阜阳市第二人民医院口腔科")
        );

        assertTrue(matched.isEmpty());
    }
}
