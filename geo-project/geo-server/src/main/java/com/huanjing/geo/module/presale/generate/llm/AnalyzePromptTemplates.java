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
              "scene_advantages": [string],
              "top_keywords": [
                {"keyword": "性价比高", "sentiment": "POSITIVE"},
                {"keyword": "等位时间长", "sentiment": "NEGATIVE"}
              ],
              "negative_evidence": {
                "has_negative": boolean,
                "snippet": <string or null>
              }
            }
            额外约束:
            1) top_keywords 最多输出 5 个元素。
            2) top_keywords[].keyword 必须是非空短词/短语。
            3) top_keywords[].sentiment 只能是 POSITIVE/NEUTRAL/NEGATIVE。
            4) negative_evidence.has_negative=false 时,negative_evidence.snippet 必须为 null。
            """;

    public static final String USER_TEMPLATE = """
            问题:{{originalPrompt}}
            回答:{{queryAnswer}}
            目标品牌:{{brandName}}
            """;
}
