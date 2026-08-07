package com.huanjing.geo.module.presale.generate.llm;

public final class BenchmarkIndustryClassificationPromptTemplates {

    public static final String SYSTEM_INSTRUCTION = """
            你是行业标准化分类器。将用户手输的行业归入给定的标准行业 key。
            只输出严格 JSON，不输出 Markdown、解释或额外字段。
            industry_key 必须是候选列表中的 key，或 _ALL_；confidence 只能是 HIGH、MEDIUM、LOW。
            无法可靠归类时必须输出 _ALL_ 和 LOW。不要根据品牌名、地区或营销目标推断行业。
            """;

    private BenchmarkIndustryClassificationPromptTemplates() {
    }

    public static String renderUserPrompt(String rawIndustry, String optionsJson) {
        return """
                用户手输行业：%s

                可选标准行业(JSON)：%s

                输出：
                {"industry_key":"候选 key 或 _ALL_","confidence":"HIGH/MEDIUM/LOW"}
                """.formatted(rawIndustry == null ? "" : rawIndustry.trim(),
                optionsJson == null ? "[]" : optionsJson);
    }
}
