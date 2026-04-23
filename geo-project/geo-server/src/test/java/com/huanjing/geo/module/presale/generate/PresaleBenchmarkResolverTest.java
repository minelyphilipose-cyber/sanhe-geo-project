package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.common.MatchLevel;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresaleBenchmarkResolverTest {

    @Test
    void exactMatch_financeAll_hitsExactAndReturnsExactLevel() {
        PresaleBenchmarkResolver resolver = createDefaultResolver();

        BenchmarksFrozen result = resolver.resolve("finance", "_ALL_");

        assertThat(result).isNotNull();
        assertThat(result.getIndustry()).isEqualTo("finance");
        assertThat(result.getIndustryRole()).isEqualTo("_ALL_");
        assertThat(result.getMatchLevel()).isEqualTo(MatchLevel.EXACT);
    }

    @Test
    void fallbackToIndustryAll_financeCto_hitsIndustryFallback() {
        PresaleBenchmarkResolver resolver = createDefaultResolver();

        BenchmarksFrozen result = resolver.resolve("finance", "CTO");

        assertThat(result).isNotNull();
        assertThat(result.getIndustry()).isEqualTo("finance");
        assertThat(result.getIndustryRole()).isEqualTo("_ALL_");
        assertThat(result.getMatchLevel()).isEqualTo(MatchLevel.FALLBACK_INDUSTRY);
    }

    @Test
    void fallbackToAllAll_unknownIndustry_hitsGlobalFallback() {
        PresaleBenchmarkResolver resolver = createDefaultResolver();

        BenchmarksFrozen result = resolver.resolve("unknown_industry", "_ALL_");

        assertThat(result).isNotNull();
        assertThat(result.getIndustry()).isEqualTo("_ALL_");
        assertThat(result.getIndustryRole()).isEqualTo("_ALL_");
        assertThat(result.getMatchLevel()).isEqualTo(MatchLevel.FALLBACK_INDUSTRY);
    }

    @Test
    void fallbackToAllAll_unknownIndustryAndRole_hitsGlobalFallback() {
        PresaleBenchmarkResolver resolver = createDefaultResolver();

        BenchmarksFrozen result = resolver.resolve("unknown_industry", "unknown_role");

        assertThat(result).isNotNull();
        assertThat(result.getIndustry()).isEqualTo("_ALL_");
        assertThat(result.getIndustryRole()).isEqualTo("_ALL_");
        assertThat(result.getMatchLevel()).isEqualTo(MatchLevel.FALLBACK_INDUSTRY);
    }

    @Test
    void fallbackToIndustryAll_allEightEnglishIndustries_hitIndustryFallback() {
        PresaleBenchmarkResolver resolver = createDefaultResolver();
        String[] industries = {
                "restaurant", "education", "automotive", "retail",
                "finance", "tourism", "medical_beauty", "tech_software"
        };

        for (String industry : industries) {
            BenchmarksFrozen result = resolver.resolve(industry, "non_exist_role");

            assertThat(result).isNotNull();
            assertThat(result.getIndustry()).isEqualTo(industry);
            assertThat(result.getIndustryRole()).isEqualTo("_ALL_");
            assertThat(result.getMatchLevel()).isEqualTo(MatchLevel.FALLBACK_INDUSTRY);
        }
    }

    @Test
    void fallbackToAllAll_blankIndustry_hitsGlobalFallback() {
        PresaleBenchmarkResolver resolver = createDefaultResolver();

        BenchmarksFrozen result = resolver.resolve("   ", "non_exist_role");

        assertThat(result).isNotNull();
        assertThat(result.getIndustry()).isEqualTo("_ALL_");
        assertThat(result.getIndustryRole()).isEqualTo("_ALL_");
        assertThat(result.getMatchLevel()).isEqualTo(MatchLevel.FALLBACK_INDUSTRY);
    }

    @Test
    void startupFailIfAllAllMissing_throwsIllegalStateException() throws Exception {
        String invalidJson = """
                {
                  "version": "v1.0",
                  "entries": [
                    {
                      "industry": "finance",
                      "industryRole": "_ALL_",
                      "industryAvg": { "overall": 62.0, "mention": 65.0, "ranking": 58.0, "sentiment": 70.0, "coverage": 55.0 },
                      "top1": { "overall": 86.0, "mention": 90.0, "ranking": 88.0, "sentiment": 85.0, "coverage": 82.0 },
                      "top10Score": 78.0,
                      "confidenceLevel": "HIGH",
                      "source": "AUTO_P50",
                      "sampleSize": 80
                    }
                  ]
                }
                """;
        Path temp = Files.createTempFile("benchmark-missing-all-all", ".json");
        Files.writeString(temp, invalidJson, StandardCharsets.UTF_8);

        PresaleBenchmarkResolver resolver = new PresaleBenchmarkResolver(
                new ObjectMapper(),
                new DefaultResourceLoader(),
                temp.toUri().toString()
        );

        assertThatThrownBy(resolver::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("(_ALL_, _ALL_)");
    }

    private PresaleBenchmarkResolver createDefaultResolver() {
        PresaleBenchmarkResolver resolver = new PresaleBenchmarkResolver(
                new ObjectMapper(),
                new DefaultResourceLoader()
        );
        resolver.init();
        return resolver;
    }
}
