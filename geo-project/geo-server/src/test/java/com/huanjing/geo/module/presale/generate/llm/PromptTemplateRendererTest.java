package com.huanjing.geo.module.presale.generate.llm;

import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptTemplateRendererTest {

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    @Test
    void render_replacesAllSupportedPlaceholders() {
        String template = "{brand}-{industry}-{industry_role}-{region}-{product}-{competitor}";

        String actual = renderer.render(
                template,
                "P001",
                ctx(2, "Top1"),
                report("Acme", "餐饮", "连锁", "上海")
        );

        assertEquals("Acme-餐饮-连锁-上海--Top1", actual);
    }

    @Test
    void render_usesEmptyStringForNullValues() {
        String template = "{brand}|{industry}|{industry_role}|{region}|{product}|{competitor}";

        String actual = renderer.render(
                template,
                "P002",
                ctx(1, null),
                report(null, "餐饮", null, null)
        );

        assertEquals("|餐饮||||", actual);
    }

    @Test
    void batch1_competitorPlaceholder_warnsButDoesNotThrow() {
        String template = "vs {competitor} by {brand}";
        String actual = renderer.render(
                template,
                "P003",
                ctx(1, "Claude"),
                report("Acme", "SaaS", "B2B", "CN")
        );
        assertEquals("vs Claude by Acme", actual);
    }

    @Test
    void batch2_competitorPlaceholder_replacedWithCtxCompetitorName() {
        String template = "{brand} vs {competitor}";
        String actual = renderer.render(
                template,
                "P004",
                ctx(2, "Gemini"),
                report("Acme", "SaaS", "B2B", "CN")
        );
        assertEquals("Acme vs Gemini", actual);
    }

    private PlatformCallContext ctx(int batchNo, String competitor) {
        return new PlatformCallContext(1L, batchNo, "kimi", 101L, competitor, "Acme", 1L, false);
    }

    private PresaleReport report(String brand, String industry, String role, String region) {
        PresaleReport report = new PresaleReport();
        report.setBrandName(brand);
        report.setIndustry(industry);
        report.setIndustryRole(role);
        report.setRegion(region);
        return report;
    }
}
