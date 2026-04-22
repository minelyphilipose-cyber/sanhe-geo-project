package com.huanjing.geo.module.presale.generate.llm;

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
                2,
                "Acme",
                "餐饮",
                "连锁",
                "上海",
                "Top1"
        );

        assertEquals("Acme-餐饮-连锁-上海--Top1", actual);
    }

    @Test
    void render_usesEmptyStringForNullValues() {
        String template = "{brand}|{industry}|{industry_role}|{region}|{product}|{competitor}";

        String actual = renderer.render(
                template,
                "P002",
                1,
                null,
                "餐饮",
                null,
                null,
                null
        );

        assertEquals("|餐饮||||", actual);
    }
}

