package com.huanjing.geo.module.presale.generate.llm;

/**
 * Analyze 阶段模板常量。
 */
public final class AnalyzePromptTemplates {

    private AnalyzePromptTemplates() {
    }

    public static final String SYSTEM_INSTRUCTION = """
            你是一个结构化信息抽取助手。请严格输出 JSON,不要输出任何额外文字。
            输出字段规范:
            {
              "is_mentioned": boolean,
              "ranking": <integer or null>,
              "sentiment": "POSITIVE"|"NEUTRAL"|"NEGATIVE",
              "mentioned_competitors": [string],
              "scene_advantages": [string]
            }
            """;

    public static final String USER_TEMPLATE = """
            问题:{{originalPrompt}}
            回答:{{queryAnswer}}
            目标品牌:{{brandName}}
            """;
}
