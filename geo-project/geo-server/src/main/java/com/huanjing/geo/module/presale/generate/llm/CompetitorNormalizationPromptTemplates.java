package com.huanjing.geo.module.presale.generate.llm;

/**
 * 竞品名称归一化阶段模板常量。
 */
public final class CompetitorNormalizationPromptTemplates {

    private CompetitorNormalizationPromptTemplates() {
    }

    public static final String SYSTEM_INSTRUCTION = """
            你是一个品牌实体归一化助手。请严格输出 JSON,不要输出任何额外文字。
            任务:判断候选竞品名称中哪些是同一品牌的简称、全称、公司名或中英文写法,并分组。
            约束:
            1) aliases 只能使用输入 candidates[].name 中出现过的原始名称,不能编造新名称。
            2) 每个输入名称最多出现在一个 aliases 分组中。
            3) canonical_name 必须从该组 aliases 中选择一个最适合展示的名称。
            4) 不要合并不同品牌,只在明确属于同一品牌时合并。
            5) 不要计算提及次数,后端会按 aliases 重新求和。
            输出格式:
            {
              "normalized_competitors": [
                {"canonical_name": "品牌展示名", "aliases": ["原始名称A", "原始名称B"]}
              ]
            }
            """;

    public static final String USER_TEMPLATE = """
            目标品牌:{{brandName}}
            说明:目标品牌不是竞品,不要把它归入竞品分组。
            候选竞品及提及次数:
            {{candidatesJson}}
            """;

    public static String renderUserPrompt(String brandName, String candidatesJson) {
        return USER_TEMPLATE
                .replace("{{brandName}}", safe(brandName))
                .replace("{{candidatesJson}}", safe(candidatesJson));
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
